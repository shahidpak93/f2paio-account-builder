package com.example.f2paio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class LimitedBody implements HttpResponse.BodySubscriber<byte[]>
{
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;

    @Override public CompletionStage<byte[]> getBody() { return body; }
    @Override public void onSubscribe(Flow.Subscription value)
    {
        subscription = value; subscription.request(1);
    }

    @Override public void onNext(List<ByteBuffer> buffers)
    {
        for (ByteBuffer buffer : buffers)
        {
            if (buffer.remaining() > Tuning.MAX_RESPONSE_BYTES - bytes.size())
            {
                subscription.cancel(); body.completeExceptionally(new IOException("Response too large")); return;
            }
            byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.write(chunk, 0, chunk.length);
        }
        subscription.request(1);
    }

    @Override public void onError(Throwable error) { body.completeExceptionally(error); }
    @Override public void onComplete() { body.complete(bytes.toByteArray()); }
}
