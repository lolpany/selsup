package org.example;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

import java.io.IOException;
import java.sql.Time;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.example.CrptApi.ApiResponse.NULL;

public class CrptApi implements AutoCloseable {

    private final long requestLimitStep;
    private final CrptApiApi crptApiApi;
    private final AtomicBoolean isOn;
    private final Queue<SubmitDocumentRequestBody> requestQueue;
    private long lastRequestSendTime;
    private final Map<String, Queue<ApiResponse>> docIdToResponseQueue = new ConcurrentHashMap<>();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimitStep = timeUnit.toNanos(1) / requestLimit;
        Retrofit retrofit = new Retrofit.Builder().baseUrl("https://ismp.crpt.ru/api/v3/").addConverterFactory(GsonConverterFactory.create()).build();
        this.crptApiApi = retrofit.create(CrptApiApi.class);
        this.isOn = new AtomicBoolean(true);
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.lastRequestSendTime = 0;
        new Thread(new Sender()).start();
    }

    public ApiResponse createDocumentForProductTurnover(SubmitDocumentRequestBody request, String signature) throws InterruptedException {
        ArrayBlockingQueue<ApiResponse> responseQueue = new ArrayBlockingQueue<>(1);
        this.docIdToResponseQueue.put(request.doc_id, responseQueue);
        this.requestQueue.add(request);
        ApiResponse response = responseQueue.take();
        this.docIdToResponseQueue.remove(request.doc_id);
        return response;
    }

    @Override
    public void close() throws Exception {
        isOn.set(false);
    }

    interface CrptApiApi {
        @POST("lk/documents/create")
        Call<ApiResponse> documentsCreate(@Body SubmitDocumentRequestBody body);
    }

    static class ApiResponse {
        static final ApiResponse NULL = new ApiResponse();
    }

    private static class SubmitDocumentRequestBody {
        Description description;
        String doc_id;
        String doc_status;
        String doc_type;
        boolean importRequest;
        String owner_inn;
        String participant_inn;
        String producer_inn;
        LocalDate production_date;
        String production_type;
        List<Product> products;
        LocalDate reg_date;
        String reg_number;

        private class Description {
            String participantInn;
        }

        private class Product {
            String certificate_document;
            LocalDate certificate_document_date;
            String certificate_document_number;
            String owner_inn;
            String producer_inn;
            LocalDate production_date;
            String tnved_code;
            String uit_code;
            String uitu_code;
        }
    }

    private class Sender implements Runnable {
        @Override
        public void run() {
            while (isOn.get()) {
                long currentTime = System.nanoTime();
                if (currentTime - lastRequestSendTime > requestLimitStep) {
                    SubmitDocumentRequestBody request = requestQueue.poll();
                    if (request != null) {
                        lastRequestSendTime = currentTime;
                        ApiResponse apiResponse = null;
                        try {
                            apiResponse = crptApiApi.documentsCreate(request).execute().body();
                        } catch (IOException e) {
                            apiResponse = NULL;
                        }
                        docIdToResponseQueue.get(request.doc_id).add(apiResponse);
                    }
                }
            }
        }
    }

}
