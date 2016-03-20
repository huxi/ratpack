/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.handling.internal;

import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import ratpack.handling.RequestId;
import ratpack.handling.RequestLogger;
import ratpack.handling.RequestOutcome;
import ratpack.handling.UserId;
import ratpack.http.HttpMethod;
import ratpack.http.Request;
import ratpack.http.SentResponse;
import ratpack.http.Status;
import ratpack.http.internal.HttpHeaderConstants;
import ratpack.util.Types;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public class NcsaRequestLogger implements RequestLogger {

  private final DateTimeFormatter formatter = DateTimeFormatter
    .ofPattern("dd/MMM/yyyy:HH:mm:ss Z")
    .withZone(ZoneId.systemDefault())
    .withLocale(Locale.ENGLISH);

  private final Logger logger;

  public NcsaRequestLogger(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void log(RequestOutcome outcome) {
    if (!logger.isInfoEnabled()) {
      return;
    }

    // TODO - use one string builder here and remove use of String.format()

    Request request = outcome.getRequest();
    SentResponse response = outcome.getResponse();
    String responseSize = "-";
    String contentLength = response.getHeaders().get(HttpHeaderConstants.CONTENT_LENGTH);
    if (contentLength != null) {
      responseSize = contentLength;
    }

    StringBuilder logLine = new StringBuilder()
      .append(
        ncsaLogFormat(
          request.getRemoteAddress(),
          "-",
          request.maybeGet(UserId.class).map(Types::cast),
          request.getTimestamp(),
          request.getMethod(),
          "/" + request.getPath(),
          request.getProtocol(),
          outcome.getResponse().getStatus(),
          responseSize));

    request.maybeGet(RequestId.class).ifPresent(id1 -> {
      logLine.append(" id=");
      logLine.append(id1);
    });

    logger.info(logLine.toString());
  }

  String ncsaLogFormat(HostAndPort client, String rfc1413Ident, Optional<CharSequence> userId, Instant timestamp, HttpMethod method, String uri, String httpProtocol, Status status, String responseSize) {
    return String.format("%s %s %s [%s] \"%s %s %s\" %d %s",
      client.getHostText(),
      rfc1413Ident,
      userId.orElse("-"),
      formatter.format(timestamp),
      method.getName(),
      uri,
      httpProtocol,
      status.getCode(),
      responseSize);
  }

  /*
   * Left package-private so formatter can be tested separately.
   * Not static so testing with different locales/timezones is possible.
   */
  String getTimestampString(Instant instant) {
    return formatter.format(instant);
  }

}
