const request = require('./utils/request.js');



const MEMBER_TABS = "home,courses,bookings,ai-chat,mine";

const COACH_TABS = "coach-home,coach-appointments,coach-students,coach-mine";



App({
  globalData: {
    token: "",
    userInfo: null,
    apiBaseUrl: "http://localhost:8080",
    appMode: "member"
  },

  onLaunch() {

    const token = wx.getStorageSync("token");

    const userInfo = wx.getStorageSync("userInfo");

    if (token) {

      this.globalData.token = token;

      this.globalData.userInfo = userInfo;

    }

    // 閹垹顦插Ο鈥崇础閿涙碍鐗撮幑顔款潡閼规彃鎷版稉濠冾偧娣囨繂鐡ㄩ惃鍕佸蹇撳枀鐎?

    if (userInfo && (userInfo.role === "trainer" || userInfo.role === "both")) {

      const savedMode = wx.getStorageSync("appMode");

      this.globalData.appMode = savedMode === "coach" || savedMode === "member" ? savedMode : "coach";

    } else {

      this.globalData.appMode = "member";

    }

    wx.setStorageSync("appMode", this.globalData.appMode);

    // 闁插秴鐣鹃崥鎴濆煂鐎电懓绨茬粩顖烆浕妞?

    if (this.globalData.appMode === "coach") {

      const pages = getCurrentPages();

      const current = pages.length > 0 ? pages[pages.length - 1].route : "";

      if (current !== "pages/coach-home/index" && !current.startsWith("pages/coach-")) {

        wx.reLaunch({ url: "/pages/coach-home/index" });

      }

    }

  },



  // 閸掑洦宕查崚棰佺窗閸涙ɑ膩瀵?

  switchToMemberMode() {

    this.globalData.appMode = "member";

    wx.setStorageSync("appMode", "member");

    wx.reLaunch({ url: "/pages/home/index" });

  },



  // 閸掑洦宕查崚鐗堟殌缂佸啯膩瀵?

  switchToCoachMode() {

    this.globalData.appMode = "coach";

    wx.setStorageSync("appMode", "coach");

    wx.reLaunch({ url: "/pages/coach-home/index" });

  },



  // 閻ц缍?

  loginByCode(phone, code, callback) {

    const self = this;

    request.post("/api/auth/login-by-code", { phone, code }, (res) => {

      if (res.data && res.data.code === 200) {

        const data = res.data;

        self.globalData.token = data.token;

        self.globalData.userInfo = data.userInfo;

        wx.setStorageSync("token", data.token);

        wx.setStorageSync("userInfo", data.userInfo);



        // 閺嶈宓佺憴鎺曞閸愬啿鐣炬妯款吇濡€崇础

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



