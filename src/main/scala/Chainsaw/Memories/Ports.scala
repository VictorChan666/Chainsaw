package Chainsaw.Memories

import spinal.core._
import spinal.core.sim._
import spinal.lib._

object RamPortType extends Enumeration {
  type RamPortType = Value
  val READ, WRITE, READWRITE = Value
}

import RamPortType._

case class XILINX_BRAM_PORT(dataWidth: Int, addressWidth: Int, portType: RamPortType = READWRITE) extends Bundle with IMasterSlave { // FIXME: may not be needed
  val hasRead = portType != WRITE
  val hasWrite = portType != READ

  val addr = UInt(addressWidth bits)
  val dataIn = if (hasWrite) Bits(dataWidth bits) else null
  val dataOut = if (hasRead) Bits(dataWidth bits) else null
  val en = Bool
  val we = if (portType != READ) Bool else null

  override def asMaster(): Unit = { // implemented as slave on a RAM, as master on a ram reader/writer
    out(addr, en)
    if (portType != WRITE) in(dataOut)
    if (portType != READ) out(dataIn, we)
  }

  def >>(that: XILINX_BRAM_PORT): Unit = { // outer >> inner, the direction is the direction of control flow, not data flow
    require(!((this.portType == READ && that.portType == WRITE) || (this.portType == WRITE && that.portType == READ)),
      "read/write port cannot be driven by write/read port")

    that.addr := addr
    that.en := en

    if (this.portType != READ && that.portType != READ) that.dataIn := dataIn
    if (this.portType != WRITE && that.portType != WRITE) dataOut := that.dataOut
    if (this.portType != READ && that.portType != READ) {
      if (this.portType == READ && that.portType == READWRITE) that.we := False
      else that.we := we
    }
  }

  def <<(that: XILINX_BRAM_PORT) = that >> this

  def doRead(addrIn: UInt) = {
    require(portType != WRITE, "cannot read through write port")
    addr := addrIn
    en := True
    if (portType == READWRITE) we := False
  }

  def doWrite(addrIn: UInt, data: Bits) = {
    require(portType != READ, "cannot write through read port")
    addr := addrIn
    dataIn := data
    en := True
    we := True
  }

  def simRead(addrIn: BigInt) = {
    require(portType != WRITE, "cannot read through write port")
    addr #= addrIn
    en #= true
    if (portType == READWRITE) we #= false
  }

  def simWrite(addrIn: BigInt, data: BigInt) = {
    addr #= addrIn
    en #= true
    we #= true
    dataIn #= data
  }

  def preAssign(): Unit = {
    addr.assignDontCare() // the pattern of using don't care as pre-assignment is of great value
    addr.allowOverride
    en.assignDontCare()
    en.allowOverride
    if (portType != READ) {
      dataIn.assignDontCare()
      dataIn.allowOverride
      we.assignDontCare()
      we.allowOverride
    }
  }
}
