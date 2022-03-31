package com.odenzo.ibkr.tws.kernel

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

def contractToIBContract(c: Contract): IBContract =
  IBContract(
    symbol = c.symbol(),
    secType = c.secIdType().getApiString,
    currency = Currency.getInstance(c.currency()),
    exchange = c.exchange()
  )

def doubleToMoney(d: Double): BigDecimal = BigDecimal(d)
def doubleToPercentage(d: Double)        = BigDecimal(d)
def doubleToAmount(d: Double)            = BigDecimal(d)

/**
 * @param d
 *   IB Client funky decimal with MIN_VALUE signifying illegal value
 * @return
 */
def decimalToOptAmount(d: Decimal): Option[BigDecimal] =
  Option.when(d != null && d.isValid)(d.value()) // TODO: Test if this handles null values
