package com.aviatainc.seedstream

import org.scalatest.{FlatSpec, Matchers}

class UtilsSpec extends FlatSpec with Matchers {
  "hexDump()" should "should format results as expected" in {
    Utils.hexDump(Utils.hex2Bin("deadbeefdeadbeefdeadbeefdeadbeef")) should be (
      """
        |deadbeefdeadbeef deadbeefdeadbeef  ........ ........
      """.stripMargin
    )
  }
}
