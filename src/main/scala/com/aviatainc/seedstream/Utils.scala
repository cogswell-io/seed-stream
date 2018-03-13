package com.aviatainc.seedstream

object Utils {
  def hexDump(buffer: List[Byte]): String = {
    val hexBuilder = new StringBuilder
    val txtBuilder = new StringBuilder
    val composeBuilder = new StringBuilder
    var offset = 0

    def dumpBuffers() = {
      composeBuilder ++= hexBuilder
      if (hexBuilder.length < 16) {
        (1 to (16 - hexBuilder.length)).foreach(_ => composeBuilder ++= "  ")
        if (hexBuilder.length < 9) composeBuilder ++= "  "
      }
      composeBuilder ++= txtBuilder
      composeBuilder += '\n'
      hexBuilder.clear()
      txtBuilder.clear()
    }

    buffer.foreach(b => {
      val pos = offset % 16

      if (offset % 16 == 0 && offset > 0) {
        dumpBuffers()
      }

      val c = b.asInstanceOf[Char]

      if (offset % 16 == 8) {
        hexBuilder ++= "  "
        txtBuilder += ' '
      }
      hexBuilder ++= "%02x".format(c)
      txtBuilder += (if (b > 31 && b < 128) c else '.')

      offset += 1
    })

    dumpBuffers()

    return composeBuilder.toString
  }
}
