package com.gym.auth;

public class LoginContext {
    private static final ThreadLocal<LoginUser> holder = new ThreadLocal<>();

    public static void set(LoginUser user) { holder.set(user); }
    public static LoginUser get() { return holder.get(); }
    public static void clear() { holder.remove(); }

    public static Long getUserId() {
        LoginUser u = get();
        return u == null ? null : u.getUserId();
    }

    public static String getRole() {
        LoginUser u = get();
        return u == null ? null : u.getRole();
    }

    public static String getPhone() {
        LoginUser u = get();
        return u == null ? null : u.getPhone();
    }

    public static class LoginUser {
        private Long userId;
        private String role;
        private String phone;
        private String nickname;

        public LoginUser() {}
        public LoginUser(Long userId, String role, String phone, String nickname) {
            this.userId = userId; this.role = role; this.phone = phone; this.nickname = nickname;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }
}
