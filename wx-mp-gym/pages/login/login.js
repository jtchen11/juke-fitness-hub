var app = getApp();
Page({
  data: { phone: "", code: "", nickname: "桔刻健身用户", avatarUrl: "", loading: false, countdown: 0 },

  onChooseAvatar: function(e) {
    this.setData({ avatarUrl: e.detail.avatarUrl });
  },

  onNicknameInput: function(e) {
    this.setData({ nickname: e.detail.value });
  },

  onPhoneInput: function(e) { this.setData({ phone: e.detail.value }); },

  onPhoneConfirm: function() {
    if (this.data.phone && this.data.phone.length === 11) {
      this.setData({ codeFocus: true });
    }
  },

  onCodeInput: function(e) { this.setData({ code: e.detail.value }); },

  sendCode: function() {
    var phone = this.data.phone;
    if (!phone || phone.length !== 11) {
      wx.showToast({ title: "请输入正确的手机号", icon: "none" });
      return;
    }
    var _this = this;
    wx.request({
      url: (app.globalData.apiBaseUrl || "http://192.168.10.8:8080") + "/api/auth/send-code",
      method: "POST",
      data: { phone: phone },
      header: { "Content-Type": "application/json" },
      success: function(res) {
        if (res.data && res.data.code === 200) {
          wx.showToast({ title: "验证码已发送", icon: "success" });
          _this.startCountdown();
        } else {
          wx.showToast({ title: "发送失败，请重试", icon: "none" });
        }
      },
      fail: function() { wx.showToast({ title: "网络异常", icon: "none" }); }
    });
  },

  startCountdown: function() {
    var _this = this;
    _this.setData({ countdown: 60 });
    var timer = setInterval(function() {
      var count = _this.data.countdown - 1;
      _this.setData({ countdown: count });
      if (count <= 0) { clearInterval(timer); }
    }, 1000);
  },

  onLogin: function() {
    var phone = this.data.phone;
    var code = this.data.code;
    var nickname = this.data.nickname || "桔刻健身用户";
    if (!phone || phone.length !== 11) {
      wx.showToast({ title: "请输入正确的手机号", icon: "none" });
      return;
    }
    if (!code || code.length < 4) {
      wx.showToast({ title: "请输入验证码", icon: "none" });
      return;
    }
    var _this = this;
    this.setData({ loading: true });
    wx.request({
      url: (app.globalData.apiBaseUrl || "http://192.168.10.8:8080") + "/api/auth/login-by-code",
      method: "POST",
      data: { phone: phone, code: code, nickname: nickname },
      header: { "Content-Type": "application/json" },
      success: function(res) {
        _this.setData({ loading: false });
        if (res.data && res.data.code === 200) {
          var data = res.data;
          app.globalData.token = data.token;
          data.userInfo.avatarUrl = _this.data.avatarUrl || "";
          app.globalData.userInfo = data.userInfo;
          wx.setStorageSync("token", data.token);
          wx.setStorageSync("userInfo", data.userInfo);

          // 根据角色设置模式
          var role = data.userInfo && data.userInfo.role;
          if (role === "trainer" || role === "both") {
            app.globalData.appMode = "coach";
            wx.setStorageSync("appMode", "coach");
          } else {
            app.globalData.appMode = "member";
            wx.setStorageSync("appMode", "member");
          }

          wx.showToast({ title: "登录成功", icon: "success" });
          setTimeout(function() {
            var target = (role === "trainer" || role === "both") ? "/pages/coach-home/index" : "/pages/home/index";
            wx.reLaunch({ url: target });
          }, 1000);
        } else {
          wx.showToast({ title: res.data.message || "验证码错误", icon: "none" });
        }
      },
      fail: function() {
        _this.setData({ loading: false });
        wx.showToast({ title: "网络异常", icon: "none" });
      }
    });
  }
});