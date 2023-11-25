package org.example;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

import java.sql.Time;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final TimeUnit timeUnit;
    private final int requestLimit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    interface CrptApiApi {
        @POST("lk/documents/create")
        Call<ApiResponse> submitJob(@Body SubmitDocumentRequestBody body);
    }

    private static class ApiResponse {
    }

    private static class SubmitDocumentRequestBody {
        Description description;

        private class Description {
            String participantInn;
        }
    }
}
