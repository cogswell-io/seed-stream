package com.aviatainc.seedstream

import javax.xml.bind.DatatypeConverter

object Utils {
  def hex2Bin(hex: String): Array[Byte] = DatatypeConverter.parseHexBinary(hex)
  def bin2Hex(bin: Array[Byte]): String = DatatypeConverter.printHexBinary(bin).toLowerCase

  def b642Bin(b64: String): Array[Byte] = DatatypeConverter.parseBase64Binary(b64)
  def bin2B64(bin: Array[Byte]): String = DatatypeConverter.printBase64Binary(bin)

  def hexDump(buffer: Array[Byte]): String = hexDump(buffer)
  def hexDump(buffer: List[Byte]): String = hexDump(buffer)

  def hexDump(buffer: Iterator[Byte]): String = {
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

      if (pos == 0 && offset > 0) {
        dumpBuffers()
      }

      val c = b.asInstanceOf[Char]

      if (pos == 8) {
        hexBuilder ++= "  "
        txtBuilder += ' '
      }
      hexBuilder ++= "%02x".format(c)
      txtBuilder += (if (b > 31 && b < 128) c else '.')

      offset += 1
    })

    dumpBuffers()

    composeBuilder.toString
  }
}
