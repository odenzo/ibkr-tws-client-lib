package com.odenzo.ibkr.tws.models

import com.ib.client.*
import com.ib.contracts.*
import com.odenzo.ibkr.models.tws.IBContract

import java.util.Currency

extension (x: IBContract)
  def toWire: Contract = {
    val contract = new Contract
    contract.symbol(x.symbol)
    contract.secType(x.secType)
    contract.currency(x.currency.toString)
    // In the API side, NASDAQ is always defined as ISLAND
    contract.exchange(x.exchange)
    contract
  }
