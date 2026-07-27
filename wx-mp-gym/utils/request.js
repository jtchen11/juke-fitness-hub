function getToken() {
  try {
    var app = getApp();
    if (app && app.globalData) return app.globalData.token || wx.getStorageSync("token") || "";
  } catch (e) {}
  return wx.getStorageSync("token") || "";
}

function getBaseUrl() {
  try {
    var app = getApp();
    if (app && app.globalData) {
      return app.globalData.apiBaseUrl || "http://localhost:8080";
    }
  } catch (e) {}
  return "http://localhost:8080";
}

function doLogout() {
  try {
    var app = getApp();
    if (app && app.logout) app.logout();
  } catch (e) {
    wx.removeStorageSync("token");
    wx.removeStorageSync("userInfo");
  }
}

function request(method, url, data, success, fail) {
  var token = getToken();
  var baseUrl = getBaseUrl();
  wx.request({
    url: baseUrl + url,
    method: method,
    data: data,
    header: { "Content-Type": "application/json", "Authorization": token ? "Bearer " + token : "" },
    timeout: 120000,
    success: function(res) {
      if (res.statusCode === 401) {
        doLogout();
        try {
          var pages = getCurrentPages();
          var page = pages[pages.length - 1];
          if (page && page.setData) page.setData({ showLoginModal: true });
        } catch (e) {}
        return;
      }
      if (typeof success === "function") success(res.data);
    },
    fail: function(err) {
      wx.showToast({ title: "\u7f51\u7edc\u5f02\u5e38", icon: "none" });
      if (typeof fail === "function") fail(err);
    }
  });
}

module.exports = {
  patch: function(url, data, success, fail) { request("PATCH", url, data, success, fail); },
  get: function(url, data, success, fail) { request("GET", url, data, success, fail); },
  post: function(url, data, success, fail) { request("POST", url, data, success, fail); },
  put: function(url, data, success, fail) { request("PUT", url, data, success, fail); },
  del: function(url, data, success, fail) { request("DELETE", url, data, success, fail); }
};
