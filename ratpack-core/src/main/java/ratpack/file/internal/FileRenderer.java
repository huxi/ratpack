/*
 * Copyright 2013 the original author or authors.
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

package ratpack.file.internal;

import io.netty.handler.codec.http.HttpHeaderNames;
import ratpack.exec.Blocking;
import ratpack.file.MimeTypes;
import ratpack.func.Action;
import ratpack.func.Factory;
import ratpack.handling.Context;
import ratpack.http.Response;
import ratpack.http.internal.HttpHeaderConstants;
import ratpack.render.RendererSupport;
import ratpack.server.internal.ServerEnvironment;
import ratpack.util.Exceptions;
import ratpack.util.internal.BoundedConcurrentHashMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;

public class FileRenderer extends RendererSupport<Path> {

  private static final boolean CACHEABLE = !ServerEnvironment.env().isDevelopment();
  private static final ConcurrentMap<Path, Optional<BasicFileAttributes>> CACHE = new BoundedConcurrentHashMap<>(10000, Runtime.getRuntime().availableProcessors());

  @Override
  public void render(Context context, Path targetFile) throws Exception {
    readAttributes(targetFile, attributes -> {
      if (attributes == null || !attributes.isRegularFile()) {
        context.clientError(404);
      } else {
        sendFile(context, targetFile, attributes);
      }
    });
  }

  public static void sendFile(Context context, Path file, BasicFileAttributes attributes) {
    if (!context.getRequest().getMethod().isGet()) {
      context.clientError(405);
      return;
    }

    Date date = new Date(attributes.lastModifiedTime().toMillis());

    context.lastModified(date, () -> {
      final String ifNoneMatch = context.getRequest().getHeaders().get(HttpHeaderNames.IF_NONE_MATCH);
      Response response = context.getResponse();
      if (ifNoneMatch != null && ifNoneMatch.trim().equals("*")) {
        response.status(NOT_MODIFIED.code()).send();
        return;
      }

      response.contentTypeIfNotSet(() -> context.get(MimeTypes.class).getContentType(file.getFileName().toString()));
      response.getHeaders().set(HttpHeaderConstants.CONTENT_LENGTH, Long.toString(attributes.size()));
      try {
        response.sendFile(file);
      } catch (Exception e) {
        throw Exceptions.uncheck(e);
      }
    });
  }

  private static Factory<BasicFileAttributes> getter(Path file) {
    return () -> {
      if (Files.exists(file)) {
        return Files.readAttributes(file, BasicFileAttributes.class);
      } else {
        return null;
      }
    };
  }

  public static void readAttributes(Path file, Action<? super BasicFileAttributes> then) throws Exception {
    if (CACHEABLE) {
      Optional<BasicFileAttributes> basicFileAttributes = CACHE.get(file);
      if (basicFileAttributes == null) {
        Blocking.get(getter(file)).then(a -> {
          CACHE.put(file, Optional.ofNullable(a));
          then.execute(a);
        });
      } else {
        then.execute(basicFileAttributes.orElse(null));
      }
    } else {
      Blocking.get(getter(file)).then(then);
    }
  }

}
