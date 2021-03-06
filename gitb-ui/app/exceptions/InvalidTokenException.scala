package exceptions

case class InvalidTokenException(error:Int, msg:String) extends Exception(msg:String){
  def getError: Int = {
    return error
  }
}