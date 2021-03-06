package exceptions

object ErrorCodes {

  //Account Service Related Errors
  val MISSING_PARAMS = 101
  val INVALID_PARAM = 102
  val INVALID_REQUEST = 103
  val INVALID_CREDENTIALS = 104
  val NOT_SUPPORTED_YET = 105
  val INVALID_ACTIVATION_CODE = 108
  val ACCOUNT_IS_SUSPENDED = 109
  val EMAIL_EXISTS:Int = 110
  val NO_SUCH_USER:Int = 111
  val EMAIL_IS_NOT_VALID:Int = 112

  //Auth Service Related Errors
  val INVALID_ACCESS_TOKEN = 201
  val INVALID_REFRESH_TOKEN = 202
  val INVALID_AUTHORIZATION_HEADER = 203
  val AUTHORIZATION_REQUIRED = 204
  val UNAUTHORIZED_ACCESS = 205

  //System Service Related Errors
  val SYSTEM_NOT_FOUND = 301

  //General Errors
  val GATEWAY_TIMEOUT = 401
}