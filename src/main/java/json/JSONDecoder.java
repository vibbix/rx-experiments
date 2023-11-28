package json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Simple JSON Decoder for turning a mapping iterator into a Flux
 * @author vibbix
 */
public class JSONDecoder {
    private final ObjectMapper mapper;
    private final Scheduler scheduler;
    public JSONDecoder() {
        this.mapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
        this.scheduler = Schedulers.boundedElastic();
    }

    public <T> Flux<T> createFluxReader(String resourcePath, TypeReference<T> type) {
        return Flux.usingWhen(Mono.fromCallable(() -> mapper.createParser(JSONDecoder.class.getResourceAsStream(resourcePath))),
                parser -> createRxJacksonParser(parser, type),
                JSONDecoder::closeSilently)
                .subscribeOn(scheduler, true);
    }

    private <T> Flux<T> createRxJacksonParser(JsonParser parser, TypeReference<T> type) {
        return Flux.usingWhen(mappingIteratorMono(parser, type),
                objMapper -> Flux.fromIterable((Iterable<? extends T>) () -> (Iterator<T>) objMapper),
                JSONDecoder::closeSilently);
    }

    private <T, E> Mono<MappingIterator<? extends T>> mappingIteratorMono(JsonParser parser, TypeReference<T> type) {
        Mono<Mono<MappingIterator<T>>> m =  Mono.fromCallable(() -> {
            try {
                //We need to skip forward to the first token to be parsed before we begin
                var jToken = parser.nextToken();
                if (jToken != JsonToken.START_ARRAY) {
                    return Mono.error(() -> new IllegalStateException("Did not start with START_ARRAY token"));
                }
                jToken = parser.nextToken();
                if (jToken != JsonToken.START_OBJECT) {
                    return Mono.error(() -> new IllegalStateException("Array does not begin with START_OBJECT token"));
                }
                MappingIterator<T> it = mapper.readValues(parser, type);
                return Mono.just(it);
            } catch (IOException e) {
                return Mono.error(e);
            }
        });
        return m.flatMap(Function.identity());
    }

    public static <T extends Closeable> Mono<?> closeSilently(T closeable) {
        return Mono.fromRunnable(() -> {
            try {
                closeable.close();
            } catch (Exception ex) {
                throw Exceptions.bubble(ex);
            }
        });
    }
}
