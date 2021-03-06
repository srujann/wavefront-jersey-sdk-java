package com.wavefront.sdk.jersey;

import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.jersey.app.SampleApp;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;
import static com.wavefront.sdk.common.Constants.WAVEFRONT_PROVIDED_SOURCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test class to test reported metric/histogram for Dropwizard (Jersey) apps requests/responses
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class JerseyAppReporterTest {

  private final SampleApp sampleApp = new SampleApp();
  private int httpPort;

  @Before
  public void setup() throws Exception {
    sampleApp.run("server");
    httpPort = sampleApp.getHttpPort();
  }

  @Test
  public void testCRUD() throws URISyntaxException, IOException {
    testCreate();
    testRead();
    testUpdate();
    testDelete();
    testGetAll();
    testError();
    testOverallAggregatedMetrics();
  }

  private void testCreate() throws IOException {
    assertEquals(204, invokePostRequest("sample/foo/bar"));

    Map<String, String> tags = new HashMap<String, String>() {{
      put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
      put(SERVICE_TAG_KEY, SampleApp.SERVICE);
      put(SHARD_TAG_KEY, SampleApp.SHARD);
      put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
      put("jersey.resource.method", "barCreate");
      put("operationName", "SampleResource.barCreate");
    }};
    // Request gauge
    assertEquals(0, sampleApp.reportedValue(new MetricName(
        "request.sample.foo.bar.POST.inflight", tags)));

    // Response counter metric
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.POST.204.cumulative", tags)));

    // Aggregated metrics (delta counters)
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.POST.204.aggregated_per_application",
        new HashMap<String, String>() {{
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barCreate");
          put("operationName", "SampleResource.barCreate");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.POST.204.aggregated_per_cluster",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barCreate");
          put("operationName", "SampleResource.barCreate");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.POST.204.aggregated_per_service",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barCreate");
          put("operationName", "SampleResource.barCreate");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.POST.204.aggregated_per_shard", new HashMap<String, String>() {{
      put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
      put(SERVICE_TAG_KEY, SampleApp.SERVICE);
      put(SHARD_TAG_KEY, SampleApp.SHARD);
      put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
      put("jersey.resource.method", "barCreate");
      put("operationName", "SampleResource.barCreate");
      put("source", WAVEFRONT_PROVIDED_SOURCE);
    }})));

    // Response latency histogram
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.POST.204.latency", tags)));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.POST.204.cpu_ns", tags)));

    // Tracing Span
    WavefrontSpan span = sampleApp.reportedSpan("SampleResource.barCreate");
    assertNotNull(span);
    List<Pair<String, String>> expectedTags = new ArrayList<>();
    expectedTags.add(new Pair<>("span.kind", "server"));
    expectedTags.add(new Pair<>("component", "jersey-server"));
    expectedTags.add(new Pair<>("http.method", "POST"));
    expectedTags.add(new Pair<>("http.url",
            "http://localhost:" + sampleApp.getHttpPort() + "/sample/foo/bar"));
    expectedTags.add(new Pair<>("http.status_code", "204"));
    expectedTags.add(new Pair<>("jersey.resource.class",
            "com.wavefront.sdk.jersey.app.SampleApp.SampleResource"));
    expectedTags.add(new Pair<>("jersey.path", "sample/foo/bar"));
    expectedTags.add(new Pair<>("application", "wavefront"));
    expectedTags.add(new Pair<>("service", SampleApp.SERVICE));
    expectedTags.add(new Pair<>("cluster", SampleApp.CLUSTER));
    expectedTags.add(new Pair<>("shard", SampleApp.SHARD));
    expectedTags.add(new Pair<>("location", "SF"));
    expectedTags.add(new Pair<>("env", "Staging"));
    assertEquals(new HashSet<>(expectedTags), new HashSet<>(span.getTagsAsList()));
  }

  private void testRead() throws IOException {
    assertEquals(200, invokeGetRequest("sample/foo/bar/123"));

    Map<String, String> tags = new HashMap<String, String>() {{
      put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
      put(SERVICE_TAG_KEY, SampleApp.SERVICE);
      put(SHARD_TAG_KEY, SampleApp.SHARD);
      put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
      put("jersey.resource.method", "barGet");
      put("operationName", "SampleResource.barGet");
    }};
    // Request gauge
    assertEquals(0, sampleApp.reportedValue(new MetricName(
        "request.sample.foo.bar._id_.GET.inflight", tags)));

    // Response counter metric
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.GET.200.cumulative", tags)));

    // Aggregated metrics (delta counters)
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.GET.200.aggregated_per_application",
        new HashMap<String, String>() {{
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barGet");
          put("operationName", "SampleResource.barGet");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.GET.200.aggregated_per_cluster",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barGet");
          put("operationName", "SampleResource.barGet");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.GET.200.aggregated_per_service",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barGet");
          put("operationName", "SampleResource.barGet");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.GET.200.aggregated_per_shard",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put(SHARD_TAG_KEY, SampleApp.SHARD);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barGet");
          put("operationName", "SampleResource.barGet");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
    }})));

    // Response latency histogram
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.GET.200.latency", tags)));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.GET.200.cpu_ns", tags)));

    // Tracing Span
    WavefrontSpan span = sampleApp.reportedSpan("SampleResource.barGet");
    assertNotNull(span);
    List<Pair<String, String>> expectedTags = new ArrayList<>();
    expectedTags.add(new Pair<>("span.kind", "server"));
    expectedTags.add(new Pair<>("component", "jersey-server"));
    expectedTags.add(new Pair<>("http.method", "GET"));
    expectedTags.add(new Pair<>("http.url",
            "http://localhost:" + sampleApp.getHttpPort() + "/sample/foo/bar/123"));
    expectedTags.add(new Pair<>("http.status_code", "200"));
    expectedTags.add(new Pair<>("jersey.resource.class",
            "com.wavefront.sdk.jersey.app.SampleApp.SampleResource"));
    expectedTags.add(new Pair<>("jersey.path", "sample/foo/bar/{id}"));
    expectedTags.add(new Pair<>("application", "wavefront"));
    expectedTags.add(new Pair<>("service", SampleApp.SERVICE));
    expectedTags.add(new Pair<>("cluster", SampleApp.CLUSTER));
    expectedTags.add(new Pair<>("shard", SampleApp.SHARD));
    expectedTags.add(new Pair<>("location", "SF"));
    expectedTags.add(new Pair<>("env", "Staging"));
    assertEquals(new HashSet<>(expectedTags), new HashSet<>(span.getTagsAsList()));
  }

  private void testUpdate() throws IOException {
    assertEquals(204, invokePutRequest("sample/foo/bar/123"));

    Map<String, String> tags = new HashMap<String, String>() {{
      put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
      put(SERVICE_TAG_KEY, SampleApp.SERVICE);
      put(SHARD_TAG_KEY, SampleApp.SHARD);
      put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
      put("jersey.resource.method", "barUpdate");
      put("operationName", "SampleResource.barUpdate");
    }};
    // Request gauge
    assertEquals(0, sampleApp.reportedValue(new MetricName(
        "request.sample.foo.bar._id_.PUT.inflight", tags)));

    // Response counter metric
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.PUT.204.cumulative", tags)));

    // Aggregated metrics (delta counters)
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.PUT.204.aggregated_per_application",
        new HashMap<String, String>() {{
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barUpdate");
          put("operationName", "SampleResource.barUpdate");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.PUT.204.aggregated_per_cluster",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barUpdate");
          put("operationName", "SampleResource.barUpdate");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.PUT.204.aggregated_per_service",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barUpdate");
          put("operationName", "SampleResource.barUpdate");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.PUT.204.aggregated_per_shard",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put(SHARD_TAG_KEY, SampleApp.SHARD);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barUpdate");
          put("operationName", "SampleResource.barUpdate");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
    }})));

    // Response latency histogram
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.PUT.204.latency", tags)));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.PUT.204.cpu_ns", tags)));

    // Tracing Span
    WavefrontSpan span = sampleApp.reportedSpan("SampleResource.barUpdate");
    assertNotNull(span);
    List<Pair<String, String>> expectedTags = new ArrayList<>();
    expectedTags.add(new Pair<>("span.kind", "server"));
    expectedTags.add(new Pair<>("component", "jersey-server"));
    expectedTags.add(new Pair<>("http.method", "PUT"));
    expectedTags.add(new Pair<>("http.url",
            "http://localhost:" + sampleApp.getHttpPort() + "/sample/foo/bar/123"));
    expectedTags.add(new Pair<>("http.status_code", "204"));
    expectedTags.add(new Pair<>("jersey.resource.class",
            "com.wavefront.sdk.jersey.app.SampleApp.SampleResource"));
    expectedTags.add(new Pair<>("jersey.path", "sample/foo/bar/{id}"));
    expectedTags.add(new Pair<>("application", "wavefront"));
    expectedTags.add(new Pair<>("service", SampleApp.SERVICE));
    expectedTags.add(new Pair<>("cluster", SampleApp.CLUSTER));
    expectedTags.add(new Pair<>("shard", SampleApp.SHARD));
    expectedTags.add(new Pair<>("location", "SF"));
    expectedTags.add(new Pair<>("env", "Staging"));
    assertEquals(new HashSet<>(expectedTags), new HashSet<>(span.getTagsAsList()));
  }

  private void testDelete() throws IOException {
    assertEquals(204, invokeDeleteRequest("sample/foo/bar/123"));

    Map<String, String> tags = new HashMap<String, String>() {{
      put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
      put(SERVICE_TAG_KEY, SampleApp.SERVICE);
      put(SHARD_TAG_KEY, SampleApp.SHARD);
      put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
      put("jersey.resource.method", "barDelete");
      put("operationName", "SampleResource.barDelete");
    }};
    // Request gauge
    assertEquals(0, sampleApp.reportedValue(new MetricName(
        "request.sample.foo.bar._id_.DELETE.inflight", tags)));

    // Response counter metric
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.DELETE.204.cumulative", tags)));

    // Aggregated metrics (delta counters)
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.DELETE.204.aggregated_per_application",
        new HashMap<String, String>() {{
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barDelete");
          put("operationName", "SampleResource.barDelete");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.DELETE.204.aggregated_per_cluster",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barDelete");
          put("operationName", "SampleResource.barDelete");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.DELETE.204.aggregated_per_service",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barDelete");
          put("operationName", "SampleResource.barDelete");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.DELETE.204.aggregated_per_shard",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put(SHARD_TAG_KEY, SampleApp.SHARD);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "barDelete");
          put("operationName", "SampleResource.barDelete");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
    }})));

    // Response latency histogram
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.DELETE.204.latency", tags)));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.DELETE.204.cpu_ns", tags)));

    // Tracing Span
    WavefrontSpan span = sampleApp.reportedSpan("SampleResource.barDelete");
    assertNotNull(span);
    List<Pair<String, String>> expectedTags = new ArrayList<>();
    expectedTags.add(new Pair<>("span.kind", "server"));
    expectedTags.add(new Pair<>("component", "jersey-server"));
    expectedTags.add(new Pair<>("http.method", "DELETE"));
    expectedTags.add(new Pair<>("http.url",
            "http://localhost:" + sampleApp.getHttpPort() + "/sample/foo/bar/123"));
    expectedTags.add(new Pair<>("http.status_code", "204"));
    expectedTags.add(new Pair<>("jersey.resource.class",
            "com.wavefront.sdk.jersey.app.SampleApp.SampleResource"));
    expectedTags.add(new Pair<>("jersey.path", "sample/foo/bar/{id}"));
    expectedTags.add(new Pair<>("application", "wavefront"));
    expectedTags.add(new Pair<>("service", SampleApp.SERVICE));
    expectedTags.add(new Pair<>("cluster", SampleApp.CLUSTER));
    expectedTags.add(new Pair<>("shard", SampleApp.SHARD));
    expectedTags.add(new Pair<>("location", "SF"));
    expectedTags.add(new Pair<>("env", "Staging"));
    assertEquals(new HashSet<>(expectedTags), new HashSet<>(span.getTagsAsList()));
  }

  private void testGetAll() throws IOException {
    assertEquals(200, invokeGetRequest("sample/foo/bar"));

    Map<String, String> tags = new HashMap<String, String>() {{
      put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
      put(SERVICE_TAG_KEY, SampleApp.SERVICE);
      put(SHARD_TAG_KEY, SampleApp.SHARD);
      put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
      put("jersey.resource.method", "getAll");
      put("operationName", "SampleResource.getAll");
    }};
    // Request gauge
    assertEquals(0, sampleApp.reportedValue(new MetricName(
        "request.sample.foo.bar.GET.inflight", tags)));

    // Response counter metric
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.GET.200.cumulative", tags)));

    // Aggregated metrics (delta counters)
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.GET.200.aggregated_per_application",
        new HashMap<String, String>() {{
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "getAll");
          put("operationName", "SampleResource.getAll");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
    }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.GET.200.aggregated_per_cluster",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "getAll");
          put("operationName", "SampleResource.getAll");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
    }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.GET.200.aggregated_per_service",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
          put("jersey.resource.method", "getAll");
          put("operationName", "SampleResource.getAll");
          put("source", WAVEFRONT_PROVIDED_SOURCE);
    }})));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.GET.200.aggregated_per_shard", new HashMap<String, String>() {{
      put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
      put(SERVICE_TAG_KEY, SampleApp.SERVICE);
      put(SHARD_TAG_KEY, SampleApp.SHARD);
      put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
      put("jersey.resource.method", "getAll");
      put("operationName", "SampleResource.getAll");
      put("source", WAVEFRONT_PROVIDED_SOURCE);
    }})));

    // Response latency histogram
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.GET.200.latency", tags)));
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar.GET.200.cpu_ns", tags)));

    // Tracing Span
    WavefrontSpan span = sampleApp.reportedSpan("SampleResource.getAll");
    assertNotNull(span);
    List<Pair<String, String>> expectedTags = new ArrayList<>();
    expectedTags.add(new Pair<>("span.kind", "server"));
    expectedTags.add(new Pair<>("component", "jersey-server"));
    expectedTags.add(new Pair<>("http.method", "GET"));
    expectedTags.add(new Pair<>("http.url",
            "http://localhost:" + sampleApp.getHttpPort() + "/sample/foo/bar"));
    expectedTags.add(new Pair<>("http.status_code", "200"));
    expectedTags.add(new Pair<>("jersey.resource.class",
            "com.wavefront.sdk.jersey.app.SampleApp.SampleResource"));
    expectedTags.add(new Pair<>("jersey.path", "sample/foo/bar"));
    expectedTags.add(new Pair<>("application", "wavefront"));
    expectedTags.add(new Pair<>("service", SampleApp.SERVICE));
    expectedTags.add(new Pair<>("cluster", SampleApp.CLUSTER));
    expectedTags.add(new Pair<>("shard", SampleApp.SHARD));
    expectedTags.add(new Pair<>("location", "SF"));
    expectedTags.add(new Pair<>("env", "Staging"));
    assertEquals(new HashSet<>(expectedTags), new HashSet<>(span.getTagsAsList()));
  }

  private void testError() throws IOException {
    invokeGetRequest("sample/foo/bar/error");
    Map<String, String> tags = new HashMap<String, String>() {{
      put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
      put(SERVICE_TAG_KEY, SampleApp.SERVICE);
      put(SHARD_TAG_KEY, SampleApp.SHARD);
      put("jersey.resource.class", SampleApp.SampleResource.class.getCanonicalName());
      put("jersey.resource.method", "barGet");
      put("operationName", "SampleResource.barGet");
    }};
    // Response counter metric
    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.sample.foo.bar._id_.GET.errors", tags)));
  }

  private int invokePostRequest(String pathSegments) throws IOException {
    HttpUrl url = new HttpUrl.Builder().scheme("http").host("localhost").port(httpPort).
        addPathSegments(pathSegments).build();
    Request request = new Request.Builder().url(url).
        post(new RequestBody() {
          @Nullable
          @Override
          public MediaType contentType() {
            return null;
          }

          @Override
          public void writeTo(BufferedSink sink) throws IOException {

          }
        }).build();
    OkHttpClient okHttpClient = new OkHttpClient().newBuilder().build();
    Response response = okHttpClient.newCall(request).execute();
    return response.code();
  }

  private int invokeGetRequest(String pathSegments) throws IOException {
    HttpUrl url = new HttpUrl.Builder().scheme("http").host("localhost").port(httpPort).
        addPathSegments(pathSegments).build();
    Request.Builder requestBuilder = new Request.Builder().url(url);
    OkHttpClient okHttpClient = new OkHttpClient().newBuilder().build();
    Request request = requestBuilder.build();
    Response response = okHttpClient.newCall(request).execute();
    return response.code();
  }

  private int invokePutRequest(String pathSegments) throws IOException {
    HttpUrl url = new HttpUrl.Builder().scheme("http").host("localhost").port(httpPort).
        addPathSegments(pathSegments).build();
    Request request = new Request.Builder().url(url).
        put(new RequestBody() {
          @Nullable
          @Override
          public MediaType contentType() {
            return null;
          }

          @Override
          public void writeTo(BufferedSink sink) throws IOException {

          }
        }).build();
    OkHttpClient okHttpClient = new OkHttpClient().newBuilder().build();
    Response response = okHttpClient.newCall(request).execute();
    return response.code();
  }

  private int invokeDeleteRequest(String pathSegments) throws IOException {
    HttpUrl url = new HttpUrl.Builder().scheme("http").host("localhost").port(httpPort).
        addPathSegments(pathSegments).build();
    Request request = new Request.Builder().url(url).
        delete(new RequestBody() {
          @Nullable
          @Override
          public MediaType contentType() {
            return null;
          }

          @Override
          public void writeTo(BufferedSink sink) throws IOException {

          }
        }).build();
    OkHttpClient okHttpClient = new OkHttpClient().newBuilder().build();
    Response response = okHttpClient.newCall(request).execute();
    return response.code();
  }

  private void testOverallAggregatedMetrics() {
    // jersey.server.total_requests.inflight gauge should be 0
    assertEquals(0, sampleApp.reportedValue(new MetricName(
        "request.inflight", new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put(SHARD_TAG_KEY, SampleApp.SHARD);
    }})));

    assertEquals(6, sampleApp.reportedValue(new MetricName(
        "response.completed.aggregated_per_source",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put(SHARD_TAG_KEY, SampleApp.SHARD);
    }})));

    assertEquals(6, sampleApp.reportedValue(new MetricName(
        "response.completed.aggregated_per_shard",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put(SHARD_TAG_KEY, SampleApp.SHARD);
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));

    assertEquals(6, sampleApp.reportedValue(new MetricName(
        "response.completed.aggregated_per_service",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));

    assertEquals(6, sampleApp.reportedValue(new MetricName(
        "response.completed.aggregated_per_cluster",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));

    assertEquals(6, sampleApp.reportedValue(new MetricName(
        "response.completed.aggregated_per_application",
        new HashMap<String, String>() {{
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));

    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.errors.aggregated_per_source",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put(SHARD_TAG_KEY, SampleApp.SHARD);
        }})));

    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.errors.aggregated_per_shard",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put(SHARD_TAG_KEY, SampleApp.SHARD);
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));

    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.errors.aggregated_per_service",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put(SERVICE_TAG_KEY, SampleApp.SERVICE);
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));

    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.errors.aggregated_per_cluster",
        new HashMap<String, String>() {{
          put(CLUSTER_TAG_KEY, SampleApp.CLUSTER);
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));

    assertEquals(1, sampleApp.reportedValue(new MetricName(
        "response.errors.aggregated_per_application",
        new HashMap<String, String>() {{
          put("source", WAVEFRONT_PROVIDED_SOURCE);
        }})));
  }
}
