package com.odenzo.ibkr.gateway.models

import com.odenzo.ibkr.models.tws.SimpleTypes.IBAccount

case class IBClientConfig(clientId: Int, account: IBAccount, gateway: ConnectionInfo)

case class ConnectionInfo(host: String = "127.0.0.1", port: Int = 7897, clientId: Int = 2)
