package com.gitb.messaging.layer.application.as2.peppol;

import com.gitb.core.Configuration;
import com.gitb.exceptions.GITBEngineInternalError;
import com.gitb.messaging.Message;
import com.gitb.messaging.ServerUtils;
import com.gitb.messaging.layer.application.as2.AS2MIC;
import com.gitb.messaging.layer.application.as2.AS2MessagingHandler;
import com.gitb.messaging.layer.application.http.HttpMessagingHandler;
import com.gitb.messaging.layer.application.https.HttpsReceiver;
import com.gitb.messaging.model.SessionContext;
import com.gitb.messaging.model.TransactionContext;
import com.gitb.types.*;
import com.gitb.utils.ConfigurationUtils;
import com.gitb.utils.XMLUtils;
import com.helger.as2lib.disposition.DispositionType;
import com.helger.as2lib.util.CAS2Header;
import com.helger.as2lib.util.javamail.ByteArrayDataSource;
import com.helger.commons.jaxb.validation.CollectingValidationEventHandler;
import com.helger.commons.mime.CMimeType;
import com.helger.peppol.sbdh.DocumentData;
import com.helger.peppol.sbdh.read.DocumentDataReadException;
import com.helger.peppol.sbdh.read.DocumentDataReader;
import com.helger.ubl.EUBL21DocumentType;
import com.helger.ubl.UBL21DocumentTypes;
import com.helger.ubl.UBL21Marshaller;
import com.sun.xml.messaging.saaj.packaging.mime.internet.InternetHeaders;
import org.apache.http.impl.BHttpConnectionBase;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unece.cefact.namespaces.sbdh.ObjectFactory;
import org.unece.cefact.namespaces.sbdh.SBDMarshaller;
import org.unece.cefact.namespaces.sbdh.StandardBusinessDocument;
import org.unece.cefact.namespaces.sbdh.StandardBusinessDocumentHeader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by senan on 11.11.2014.
 */
public class PeppolAS2Receiver extends HttpsReceiver{
    private Logger logger = LoggerFactory.getLogger(PeppolAS2Receiver.class);

    public PeppolAS2Receiver(SessionContext sessionContext, TransactionContext transactionContext) {
        super(sessionContext, transactionContext);
    }

    @Override
    public Message receive(List<Configuration> configurations) throws Exception {
        Message received =  super.receive(configurations);

        //extract body and headers
        MapType headers = (MapType) received.getFragments().get(HttpMessagingHandler.HTTP_HEADERS_FIELD_NAME);
        BinaryType body = (BinaryType) received.getFragments().get(HttpMessagingHandler.HTTP_BODY_FIELD_NAME);

        //construct receive output
        Message message = new Message();
        message.getFragments().put(PeppolAS2MessagingHandler.HTTP_HEADERS_FIELD_NAME, headers);

        //check AS2 headers
        if(!checkAS2Headers(headers)){
            //check fail; return immediately since there is no point to carry on
            return message;
        }

        //put data into a MimeBody
        MimeBodyPart mimeBody;
        try{
            mimeBody = createMimeBody(headers, body);
        } catch (Exception e) {
            saveError("unexpected-processing-error", "An error occurred while parsing MIME content. " +
                    "Please check the following error description: " + e.getMessage());
            return message;
        }

        //verify MimeBody (with user's public certificate) which is previously signed by the sender
        try{
            mimeBody = AS2MessagingHandler.verify(mimeBody, AS2MessagingHandler.getSUTCertificate(transaction));
        } catch (Exception e) {
            saveError("integrity-check-failed", "An error occurred while verifying received message content with the public certificate provided. " +
                    "Please check the following error description: " + e.getMessage());
            return message;
        }

        //use the connection retrieved from the transaction
        connection = transaction.getParameter(BHttpConnectionBase.class);

        //connection is a server connection and will receive AS2 message
        if (connection instanceof DefaultBHttpServerConnection) {
            message = processAS2Message(mimeBody, message, headers, configurations);
        }

        //connection has sent an AS2 message and will receive AS2 MDN
        if(connection instanceof DefaultBHttpClientConnection) {
            processMDN(mimeBody);
        }

        return message;
    }

    private Message processAS2Message(MimeBodyPart mimeBody, Message message, MapType headers, List<Configuration> configurations){
        //create StandardBusinessDocument
        StandardBusinessDocument sbd;
        try {
            sbd = new SBDMarshaller().read (mimeBody.getInputStream());
        } catch (Exception e) {
            saveError("unexpected-processing-error", "Failed to interpret the passed document as a Standard Business Document");
            return message;
        }

        //create StandardBusinessDocumentHeader
        StandardBusinessDocumentHeader sbdh = sbd.getStandardBusinessDocumentHeader();
        Node sbdhNode;
        try {
            sbdhNode = XMLUtils.marshalToNode(new ObjectFactory().createStandardBusinessDocumentHeader(sbdh));
        } catch (JAXBException e) {
            //not likely to happen, since a possible exception has already been caught above
            saveError("unexpected-processing-error", "Invalid Standard Business Document Header (SBDH).");
            return message;
        }
        ObjectType businessHeader = new ObjectType(sbdhNode);

        //check business header for missing fields
        if(!checkSBDH((Node)businessHeader.getValue(), configurations)){
            return message;
        }

        //create Business Message, i.e, Invoice, Order, etc
        Element businessMessageNode;
        try {
            DocumentData documentData = new DocumentDataReader().extractData (sbd);
            businessMessageNode = documentData.getBusinessMessage ();

            // Try to determine the UBL document type from the namespace URI
            EUBL21DocumentType documentType = UBL21DocumentTypes.getDocumentTypeOfNamespace(businessMessageNode.getNamespaceURI());
            if(documentType == null) {
                saveError("unexpected-processing-error", "The business message is not a supported UBL 2.1 document");
                return message;
            }

            //Validate UBL document
            final CollectingValidationEventHandler handler = new CollectingValidationEventHandler ();
            final Object ublDocument = UBL21Marshaller.readUBLDocument(businessMessageNode,
                    documentType.getImplementationClass(), handler);

            if(ublDocument == null) {
                saveError("unexpected-processing-error", "Failed to read the UBL document as " +
                        documentType.name () + ":\n" +
                        handler.getResourceErrors ().getAllResourceErrors ().toString ());
                return message;
            }
        } catch (Exception e) {
            saveError("unexpected-processing-error", "Invalid business message.");
            return message;
        }

        ObjectType businessMessage = new ObjectType(businessMessageNode);

        //calculate MIC (Message Integrity Check)
        StringType mic = new StringType();
        try {
            mic.setValue(AS2MessagingHandler.calculateMIC(mimeBody, headers, true));
        } catch (Exception e) {
            saveError("unexpected-processing-error", "An error occurred while calculating MIC (Message Integrity Check). " +
                    "Please check the following error description: " + e.getMessage());
            return message;
        }
        logger.debug("MIC calculated: " + mic.getValue());
        transaction.setParameter(AS2MIC.class, new AS2MIC((String)mic.getValue())); //save to transaction

        //put business header, business message and MIC value into output
        message.getFragments().put(PeppolAS2MessagingHandler.BUSINESS_HEADER_FIELD_NAME,  businessHeader);
        message.getFragments().put(PeppolAS2MessagingHandler.BUSINESS_MESSAGE_FIELD_NAME, businessMessage);
        message.getFragments().put(PeppolAS2MessagingHandler.AS2_MDN_FIELD_NAME,          mic);

        transaction.setParameter(DispositionType.class, DispositionType.createSuccess());
        return message;
    }

    private void processMDN(MimeBodyPart mimeBody) throws Exception{
        String originalMIC = transaction.getParameter(AS2MIC.class).getMessageIntegrityCheck();
        String receivedMIC = checkMDN(mimeBody, originalMIC);
        StringType mic = new StringType(receivedMIC);
        logger.debug("MIC received: " + mic.getValue());
    }

    private boolean checkAS2Headers(MapType headers){
        String as2From  = ServerUtils.getHeader(headers, CAS2Header.HEADER_AS2_FROM);
        String as2To    = ServerUtils.getHeader(headers, CAS2Header.HEADER_AS2_TO);
        boolean success = true;

        if(as2From == null){
            saveError("unexpected-processing-error", "No AS2-From header found.");
            success = false;
        }
        if(as2To == null) {
            saveError("unexpected-processing-error", "No AS2-To header found.");
            success = false;
        }
        if (as2From != null && as2From.equals(as2To)){
            saveError("sender-equals-receiver", "AS2-From header is equal to AS2-To header (" + as2From + ")");
            success = false;
        }

        return success;
    }

    private String checkMDN(MimeBodyPart mimeBody, String originalMIC) {
        String receivedMIC = null;

        try{
            MimeMultipart reportParts = new MimeMultipart(mimeBody.getDataHandler().getDataSource());
            ContentType reportType = new ContentType (reportParts.getContentType ());
            String disposition = null;

            if (reportType.getBaseType().equalsIgnoreCase("multipart/report")) {
                int reportCount = reportParts.getCount();

                for (int j = 0; j < reportCount; j++)
                {
                    final MimeBodyPart reportPart = (MimeBodyPart) reportParts.getBodyPart (j);
                    if (reportPart.isMimeType (CMimeType.TEXT_PLAIN.getAsString ())){
                        //do nothing, since we only care about the MIC (Message Integrity Check)
                    }
                    else if(reportPart.isMimeType("message/disposition-notification"))  {
                        InternetHeaders headers = new InternetHeaders (reportPart.getInputStream ());
                        receivedMIC = headers.getHeader(AS2MessagingHandler.HEADER_RECEIVED_CONTENT_MIC, ", ");
                        disposition = headers.getHeader(AS2MessagingHandler.HEADER_DISPOSITION, ", ");
                    }
                }
            }

            //validate disposition type
            DispositionType.createFromString(disposition).validate();
        } catch (Exception e) {
            throw new GITBEngineInternalError("An error occurred while parsing MDN content. " +
                    "Please check the following error description: " + e.getMessage());
        }

        //compare MICs for equality
        if (receivedMIC == null || !receivedMIC.replaceAll (" ", "").equals (originalMIC.replaceAll (" ", ""))) {
            throw new GITBEngineInternalError("MIC is not matched, original MIC: " +  originalMIC + " received MIC: " + receivedMIC);
        }

        logger.debug("MICs matched, MIC: " + originalMIC);

        return receivedMIC;

    }

    private MimeBodyPart createMimeBody(MapType headers, BinaryType body) throws Exception{
        ContentType contentType = new ContentType(ServerUtils.getHeader(headers, CAS2Header.HEADER_CONTENT_TYPE));
        String receivedContentType = contentType.toString();

        byte[] data = (byte[]) body.getValue();
        MimeBodyPart mimeBody = new MimeBodyPart ();
        mimeBody.setDataHandler(new DataHandler(new ByteArrayDataSource(data, receivedContentType, null)));
        mimeBody.setHeader(CAS2Header.HEADER_CONTENT_TYPE, receivedContentType);

        return mimeBody;
    }

    private Node getBusinessDocument(MimeBodyPart mimeBody){
        InputStream businessDocument = null;
        try {
            businessDocument = mimeBody.getInputStream();
        } catch (Exception e) {
            saveError("unexpected-processing-error", "An error occurred while extracting business message from MIME content. " +
                    "Please check the following error description: " + e.getMessage());
            return null;
        }
        InputSource source = new InputSource(businessDocument);
        Document document;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source);
        } catch (Exception e) {
            saveError("unexpected-processing-error", "An error occurred while parsing business message. " +
                    "Please check the following error description: " + e.getMessage());
            return null;
        }
        return document.getFirstChild();
    }

    private ObjectType getSBDH(Node sbd) {
        //SBDH = Standard Business Document Header
        if (sbd.getChildNodes().getLength() > 0) {
            Node sbdh = sbd.getChildNodes().item(0);
            return new ObjectType(sbdh);
        }
        return null;
    }

    private boolean checkSBDH(Node sbdh, List<Configuration> configurations){
        boolean documentIdentifierFound = false;
        boolean processIdentifierFound  = false;

        Configuration documentIdentifierConfiguration = ConfigurationUtils.getConfiguration(configurations, PeppolAS2MessagingHandler.DOCUMENT_IDENTIFIER_CONFIG_NAME);
        Configuration processIdentifierConfiguration = ConfigurationUtils.getConfiguration(configurations,  PeppolAS2MessagingHandler.PROCESS_IDENTIFIER_CONFIG_NAME);

        if(documentIdentifierConfiguration == null || processIdentifierConfiguration == null) {
            throw new GITBEngineInternalError("Document Identifier or Process Identifier configurations can not be null" +
                    " when receiving PEPPOL messages");
        }

        String documentIndetifier = documentIdentifierConfiguration.getValue();
        String processIndetifier  = processIdentifierConfiguration.getValue();

        Node businessScope = XMLUtils.getChildNodeByName(sbdh, "BusinessScope");
        if(businessScope != null){
            for(int i=0; i<businessScope.getChildNodes().getLength(); i++){
                Node scope = businessScope.getChildNodes().item(i);
                Node type  = XMLUtils.getChildNodeByName(scope, "Type");
                Node id    = XMLUtils.getChildNodeByName(scope, "InstanceIdentifier");

                if(type == null) {
                    saveError("unexpected-processor-error", "No 'Type' element " +
                            "found in received standard business document header");
                    return false;
                }

                if(id == null){
                    saveError("unexpected-processor-error", "No 'InstanceIdentifier' element " +
                            "found in received standard business document header");
                    return false;
                }

                if(type.getTextContent().equals("DOCUMENTID")) {
                    if(id.getTextContent().equals(documentIndetifier)){
                        documentIdentifierFound = true;
                    }
                } else if(type.getTextContent().equals("PROCESSID")){
                    if(id.getTextContent().equals(processIndetifier)){
                        processIdentifierFound = true;
                    }
                }
            }

            if(!documentIdentifierFound) {
                saveError("document-type-id-not-accepted", "No document identifier with value '" +
                        ""+documentIndetifier + "' found in received standard business document header");
            }

            if(!processIdentifierFound){
                saveError("process-id-not-accepted", "No process identifier with value '" +
                        ""+processIndetifier + "' found in received standard business document header");
            }

            return documentIdentifierFound && processIdentifierFound;
        } else {
            if(!processIdentifierFound){
                saveError("unexpected-processor-error", "No 'BusinessScope' element " +
                        "found in received standard business document header");
            }
            return false;
        }
    }

    private ObjectType getBusinessMessage(Node sbd) {
        if (sbd.getChildNodes().getLength() > 1) {
            Node businessMessage = sbd.getChildNodes().item(1);
            return new ObjectType(businessMessage);
        }
        return null;
    }

    private void saveError(String reason, String message) {
        DispositionType dispositionType = DispositionType.createError (reason);
        transaction.setParameter(DispositionType.class, dispositionType);
        transaction.addNonCriticalError(new GITBEngineInternalError(message));
    }
}
