package com.shane.customer_gateway.constant;

import java.util.List;

public final class Constants {

    public final static String USER_ID = "user_id";
    public final static String USER_IP = "user_ip";

    public static final List<String> NEED_AUTH_PATH_LIST = List.of(
            "/customer/web/test_auth",
            "/customer/web/test_auth2"
    );
}
