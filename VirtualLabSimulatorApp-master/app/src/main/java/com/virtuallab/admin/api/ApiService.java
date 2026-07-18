package com.virtuallab.admin.api;

import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.DdosBlockedIp;
import com.virtuallab.admin.model.DdosOverview;
import com.virtuallab.admin.model.DdosTopIp;
import com.virtuallab.admin.model.DdosRecentRequest;
import com.virtuallab.admin.model.DdosRatePoint;
import com.virtuallab.admin.model.LoginRequest;
import com.virtuallab.admin.model.LoginResponseData;
import com.virtuallab.admin.model.VerifyOtpRequest;
import com.virtuallab.admin.model.Practical;
import com.virtuallab.admin.model.Stats;
import com.virtuallab.admin.model.Ticket;
import com.virtuallab.admin.model.TicketActionRequest;
import com.virtuallab.admin.model.User;
import com.virtuallab.admin.model.SettingsData;
import com.virtuallab.admin.model.SettingsUpdateRequest;
import com.virtuallab.admin.model.UpdatesData;
import com.virtuallab.admin.model.AppUpdateData;
import com.virtuallab.admin.model.Department;
import com.virtuallab.admin.model.DepartmentActionRequest;
import com.virtuallab.admin.model.Lab;
import com.virtuallab.admin.model.LabActionRequest;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    @POST("auth.php")
    Call<ApiResponse<LoginResponseData>> login(@Body LoginRequest request);

    @POST("verify_otp.php")
    Call<ApiResponse<LoginResponseData>> verifyOtp(@Body VerifyOtpRequest request);

    @GET("dashboard.php")
    Call<ApiResponse<Stats>> dashboard();

    @GET("tickets.php")
    Call<ApiResponse<List<Ticket>>> tickets(@Query("status") String status);

    @GET("tickets.php")
    Call<ApiResponse<List<com.virtuallab.admin.model.TicketMessage>>> ticketMessages(@Query("action") String action, @Query("ticket_id") int ticketId);

    @GET("letters.php")
    Call<ApiResponse<List<com.virtuallab.admin.model.Letter>>> getLetters(@Query("action") String action);

    @GET("letters.php")
    Call<ApiResponse<Object>> deleteLetter(@Query("action") String action, @Query("letter_id") String letterId);

    @POST("tickets.php")
    Call<ApiResponse<Object>> ticketAction(@Body TicketActionRequest request);

    @GET("users.php")
    Call<ApiResponse<List<User>>> users();

    @GET("settings.php")
    Call<ApiResponse<SettingsData>> settings();

    @POST("settings.php")
    Call<ApiResponse<SettingsData>> updateSettings(@Body SettingsUpdateRequest request);

    @GET("updates.php")
    Call<ApiResponse<UpdatesData>> updates();

    @GET("app_update.php")
    Call<ApiResponse<AppUpdateData>> appUpdate(@Query("platform") String platform, @Query("current_version") String currentVersion);

    @GET("practicals.php")
    Call<ApiResponse<List<Practical>>> practicals();

    @GET("departments.php")
    Call<ApiResponse<List<Department>>> departments();

    @POST("departments.php")
    Call<ApiResponse<Object>> departmentAction(@Body DepartmentActionRequest request);

    @GET("labs.php")
    Call<ApiResponse<List<Lab>>> labs(@Query("department_id") Integer departmentId, @Query("q") String q);

    @POST("labs.php")
    Call<ApiResponse<Object>> labAction(@Body LabActionRequest request);

    @GET("ddos.php")
    Call<ApiResponse<DdosOverview>> ddosOverview(@Query("action") String action);

    @GET("ddos.php")
    Call<ApiResponse<List<DdosBlockedIp>>> ddosBlocked(@Query("action") String action);

    @GET("ddos.php")
    Call<ApiResponse<List<DdosTopIp>>> ddosTopIps(@Query("action") String action);

    @GET("ddos.php")
    Call<ApiResponse<List<DdosRecentRequest>>> ddosRecent(@Query("action") String action);

    @GET("ddos.php")
    Call<ApiResponse<List<DdosRatePoint>>> ddosRateHistory(@Query("action") String action);

    @POST("ddos.php?action=block")
    Call<ApiResponse<Map<String, Object>>> ddosBlock(@Body Map<String, Object> body);

    @POST("ddos.php?action=unblock")
    Call<ApiResponse<Map<String, Object>>> ddosUnblock(@Body Map<String, Object> body);
}
