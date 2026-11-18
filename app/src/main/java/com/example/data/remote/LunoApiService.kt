package com.example.data.remote

import com.example.data.model.CoinGeckoMarketChartResponse
import com.example.data.model.LunoBalanceResponse
import com.example.data.model.LunoCandlesResponse
import com.example.data.model.LunoListOrdersResponse
import com.example.data.model.LunoOrderBookTopResponse
import com.example.data.model.LunoPostOrderResponse
import com.example.data.model.LunoTickerResponse
import com.example.data.model.LunoTradesResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LunoApiService {

  @GET("api/1/ticker")
  suspend fun getTicker(@Query("pair") pair: String): LunoTickerResponse

  @GET("api/1/orderbook_top")
  suspend fun getOrderBookTop(@Query("pair") pair: String): LunoOrderBookTopResponse

  @GET("api/1/trades")
  suspend fun getTrades(
    @Query("pair") pair: String,
    @Query("since") since: Long? = null,
  ): LunoTradesResponse

  @GET("api/exchange/1/candles")
  suspend fun getCandles(
    @Query("pair") pair: String,
    @Query("since") since: Long,
    @Query("duration") duration: Int,
    @Header("Authorization") authorization: String? = null,
    @Header("X-Timestamp") timestamp: String? = null,
    @Header("X-HMAC-SHA256-Signature") signature: String? = null,
  ): LunoCandlesResponse

  @GET("api/1/balance")
  suspend fun getBalance(
    @Header("Authorization") authorization: String,
    @Header("X-Timestamp") timestamp: String,
    @Header("X-HMAC-SHA256-Signature") signature: String,
  ): LunoBalanceResponse

  @GET("api/1/listorders")
  suspend fun getPendingOrders(
    @Header("Authorization") authorization: String,
    @Header("X-Timestamp") timestamp: String,
    @Header("X-HMAC-SHA256-Signature") signature: String,
    @Query("pair") pair: String,
    @Query("state") state: String = "PENDING",
  ): LunoListOrdersResponse

  @FormUrlEncoded
  @POST("api/1/postorder")
  suspend fun postLimitOrder(
    @Header("Authorization") authorization: String,
    @Header("X-Timestamp") timestamp: String,
    @Header("X-HMAC-SHA256-Signature") signature: String,
    @Field("pair") pair: String,
    @Field("type") type: String,
    @Field("volume") volume: String,
    @Field("price") price: String,
    @Field("post_only") postOnly: Boolean = true,
  ): LunoPostOrderResponse
}

interface CoinGeckoApiService {

  @GET("api/v3/coins/{id}/market_chart")
  suspend fun getMarketChart(
    @Path("id") coinId: String,
    @Query("vs_currency") vsCurrency: String,
    @Query("days") days: Int,
  ): CoinGeckoMarketChartResponse
}
