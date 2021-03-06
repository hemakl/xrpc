/*
 * Copyright 2018 Nordstrom, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nordstrom.xrpc.server;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.util.AttributeKey;

@ChannelHandler.Sharable
public class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {
  private static final AttributeKey<ServerContext> CONNECTION_CONTEXT =
      AttributeKey.valueOf("ServerContext");
  private final UrlRouter router;
  private final ServerContext xctx;
  private final CorsConfig corsConfig;
  private final int maxPayloadBytes;

  protected Http2OrHttpHandler(
      UrlRouter router, ServerContext xctx, CorsConfig corsConfig, int maxPayloadBytes) {
    super(ApplicationProtocolNames.HTTP_1_1);
    this.router = router;
    this.xctx = xctx;
    this.corsConfig = corsConfig;
    this.maxPayloadBytes = maxPayloadBytes;
  }

  @Override
  protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
    ctx.channel().attr(CONNECTION_CONTEXT).set(xctx);

    if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
      ChannelPipeline cp = ctx.pipeline();
      cp.addLast(
          "codec",
          new Http2HandlerBuilder()
              .maxPayloadBytes(maxPayloadBytes)
              .corsHandler(new Http2CorsHandler(corsConfig))
              .build());
      return;
    }

    if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
      ChannelPipeline cp = ctx.pipeline();
      cp.addLast("codec", new HttpServerCodec());
      cp.addLast("aggregator", new HttpObjectAggregator(maxPayloadBytes));

      if (corsConfig.isCorsSupportEnabled()) {
        cp.addLast("cors", new CorsHandler(corsConfig));
      }
      // cp.addLast("authHandler", new NoOpHandler()); // TODO(JR): OAuth2.0 Impl needed
      cp.addLast("routingFilter", router);
      return;
    }

    throw new IllegalStateException("unknown protocol: " + protocol);
  }
}
