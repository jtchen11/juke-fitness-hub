const request = require('./utils/request.js');

const MEMBER_TABS = "home,courses,bookings,ai-chat,mine";
const COACH_TABS = "coach-home,coach-appointments,coach-students,coach-mine";

App({
  globalData: {
    token: "",
    userInfo: null,
    apiBaseUrl: "http://localhost:8080",
    appMode: "member" // "member" | "coach"
  },

  onLaunch() {
    const token = wx.getStorageSync("token");
    const userInfo = wx.getStorageSync("userInfo");
    const savedMode = wx.getStorageSync("appMode");
    if (token) {
      this.globalData.token = token;
      this.globalData.userInfo = userInfo;
    }
    // 恢复模式：教练角色默认教练模式，否则会员模式
    if (userInfo && (userInfo.role === "trainer" || userInfo.role === "both")) {
      this.globalData.appMode = savedMode || "coach";
    } else {
      this.globalData.appMode = "member";
    }
    wx.setStorageSync("appMode", this.globalData.appMode);
  },

  // 切换到会员模式
  switchToMemberMode() {
    this.globalData.appMode = "member";
    wx.setStorageSync("appMode", "member");
    wx.reLaunch({ url: "/pages/home/index" });
  },

  // 切换到教练模式
  switchToCoachMode() {
    this.globalData.appMode = "coach";
    wx.setStorageSync("appMode", "coach");
    wx.reLaunch({ url: "/pages/coach-home/index" });
  },

  // 登录
  loginByCode(phone, code, callback) {
    const self = this;
    request.post("/api/auth/login-by-code", { phone, code }, (res) => {
      if (res.data && res.data.code === 200) {
        const data = res.data;
        self.globalData.token = data.token;
        self.globalData.userInfo = data.userInfo;
        wx.setStorageSync("token", data.token);
        wx.setStorageSync("userInfo", data.userInfo);

        // 根据角色决定默认模式
        const role = data.userInfo && data.userInfo.role;
        if (role === "trainer" || role === "both") {
          self.globalData.appMode = "coach";
          wx.setStorageSync("appMode", "coach");
        } else {
          self.globalData.appMode = "member";
          wx.setStorageSync("appMode", "member");
        }
      }
      typeof callback === "function" && callback(res.data || res);
    });
  },

  isLoggedIn() {
    return !!this.globalData.token;
  },

  requireLogin(callback) {
    if (this.isLoggedIn()) { typeof callback === "function" && callback(); return; }
    wx.navigateTo({ url: "/pages/login/login" });
  },

  logout() {
    this.globalData.token = "";
    this.globalData.userInfo = null;
    this.globalData.appMode = "member";
    wx.removeStorageSync("token");
    wx.removeStorageSync("userInfo");
    wx.setStorageSync("appMode", "member");
  }
});