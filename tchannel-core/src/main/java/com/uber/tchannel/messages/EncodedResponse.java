/*
 * Copyright (c) 2015 Uber Technologies, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.uber.tchannel.messages;

import com.google.common.collect.ImmutableMap;
import com.uber.tchannel.api.ResponseCode;
import com.uber.tchannel.headers.ArgScheme;
import com.uber.tchannel.utils.TChannelUtilities;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public abstract class EncodedResponse<T> extends Response {

    private static final Map<ArgScheme, Serializer.SerializerInterface> DEFAULT_SERIALIZERS = ImmutableMap.of(
        ArgScheme.JSON, new JSONSerializer(),
        ArgScheme.THRIFT, new ThriftSerializer()
    );

    private static final Serializer serializer = new Serializer(DEFAULT_SERIALIZERS);

    protected Map<String, String> headers;
    protected T body = null;

    protected EncodedResponse(@NotNull Builder<T> builder) {
        super(builder);
        this.headers = builder.headers;
        this.body = builder.body;
    }

    protected EncodedResponse(
        long id,
        ResponseCode responseCode,
        Map<String, String> transportHeaders,
        ByteBuf arg2,
        ByteBuf arg3
    ) {
        super(id, responseCode, transportHeaders, arg2, arg3);
    }

    protected EncodedResponse(@NotNull ErrorResponse error) {
        super(error);
    }

    public Map<String, String> getHeaders() {
        if (headers == null) {
            if (arg2 == null) {
                return new HashMap<>();
            } else {
                headers = serializer.decodeHeaders(this);
            }
        }

        return headers;
    }

    public String getHeader(String key) {
        return getHeaders().get(key);
    }

    public T getBody(Class<T> bodyType) {
        if (body == null && arg3 != null) {
            body = serializer.decodeBody(this, bodyType);
        }
        return body;
    }

    @Override
    public String toString() {
        if (isError()) {
            return getError().toString();
        }
        return String.format(
                "<%s responseCode=%s transportHeaders=%s headers=%s body=%s>",
                this.getClass().getSimpleName(),
                this.responseCode,
                this.transportHeaders,
                this.headers,
                this.body
        );
    }

    /**
     * @param <T> response body type
     */
    public static class Builder<T> extends Response.Builder {

        private Map<String, String> headers = new HashMap<>();
        private T body;

        protected ArgScheme argScheme;

        public Builder(@NotNull Request req) {
            super(req);
        }

        @Override
        public @NotNull Builder<T> setArg2(ByteBuf arg2) {
            if (arg2 != null && !this.headers.isEmpty()) {
                throw new IllegalStateException("Cannot set both `arg2` and `headers`.");
            }
            super.setArg2(arg2);
            return this;
        }

        @Override
        public @NotNull Builder<T> setArg3(ByteBuf arg3) {
            if (arg3 != null && this.body != null) {
                throw new IllegalStateException("Cannot set both `arg3` and `body`.");
            }
            super.setArg3(arg3);
            return this;
        }

        public @NotNull Builder<T> setHeader(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public @NotNull Builder<T> setHeaders(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public @NotNull Builder<T> setBody(T body) {
            this.body = body;
            return this;
        }

        private @NotNull Builder<T> validateHeader() {
            if (arg2 == null) {
                arg2 = serializer.encodeHeaders(this.headers, argScheme);
            } else {
                headers = null;
            }
            return this;
        }

        private @NotNull Builder<T> validateBody() {
            if (arg3 == null) {
                arg3 = body == null ? TChannelUtilities.emptyByteBuf : serializer.encodeBody(this.body, argScheme);
            } else {
                body = null;
            }
            return this;
        }

        @Override
        public @NotNull Builder<T> validate() throws IllegalStateException {
            super.validate();
            this.validateHeader();
            this.validateBody();

            if (responseCode == null) {
                throw new IllegalStateException("`responseCode` cannot be null.");
            }

            return this;
        }

    }

}
