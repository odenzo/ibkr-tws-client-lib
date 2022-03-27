package com.odenzo.ibkr.gateway.models

import com.ib.client.*
import com.ib.contracts.*
import com.odenzo.ibkr.models.tws.IBContract

import java.util.Currency

object Convertors:
  def toWire(v: IBContract): Contract = {
    val contract = new Contract
    contract.symbol(v.symbol)
    contract.secType(v.secType)
    contract.currency(v.currency.toString)
    // In the API side, NASDAQ is always defined as ISLAND
    contract.exchange(v.exchange)
    contract
  }
