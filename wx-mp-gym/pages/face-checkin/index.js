var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { statusText: '准备就绪，请面向摄像头', isRegistered: false, memberId: 0 },

  onShow() {
    var u = app.globalData.userInfo || {};
    this.setData({ memberId: u.memberId || 0 });
    this.checkPermission();
    if (this.data.memberId) {
      var _this = this;
      request.get('/api/face/check', { userId: this.data.memberId }, function(r) {
        if (r && r.registered) _this.setData({ isRegistered: true, statusText: '请点击开始打卡' });
      });
    }
  },

  checkPermission() {
    var _this = this;
    wx.getSetting({
      success: function(res) {
        if (!res.authSetting['scope.camera']) {
          wx.authorize({
            scope: 'scope.camera',
            fail: function() {
              _this.setData({ statusText: '请授权摄像头权限后使用' });
              wx.showModal({
                title: '提示',
                content: '需要摄像头权限才能使用刷脸打卡功能',
                success: function(r) {
                  if (r.confirm) wx.openSetting();
                }
              });
            }
          });
        }
      }
    });
  },

  onCameraError(e) {
    this.setData({ statusText: '摄像头异常，请检查权限' });
  },

  onCapture() {
    var _this = this;
    var ctx = wx.createCameraContext();
    ctx.takePhoto({
      quality: 'high',
      success: function(res) {
        _this.setData({ statusText: '正在验证人脸...' });
        _this.verifyFace(res.tempImagePath);
      },
      fail: function() {
        wx.showToast({ title: '拍照失败', icon: 'none' });
      }
    });
  },

  verifyFace(imagePath) {
    var _this = this;
    wx.uploadFile({
      url: (app.globalData.apiBaseUrl || 'http://localhost:8080') + '/api/face/verify',
      filePath: imagePath,
      name: 'image',
      formData: { userId: String(this.data.memberId) },
      success: function(res) {
        try {
          var data = JSON.parse(res.data);
          if (data.success) {
            _this.doCheckIn();
          } else {
            _this.setData({ statusText: data.message || '人脸验证失败，请重试' });
          }
        } catch(e) {
          _this.setData({ statusText: '验证异常，请重试' });
        }
      },
      fail: function() {
        _this.setData({ statusText: '网络异常，请检查服务是否启动' });
      }
    });
  },

  doCheckIn() {
    var _this = this;
    this.setData({ statusText: '验证成功，正在打卡...' });
    wx.getLocation({
      type: 'wgs84',
      success: function(loc) {
        request.post('/api/check-in/member/' + _this.data.memberId, { latitude: loc.latitude, longitude: loc.longitude }, function(r) {
          if (r && r.success) {
            wx.showToast({ title: '打卡成功' });
            _this.setData({ statusText: '打卡成功！+1积分' });
          } else {
            _this.setData({ statusText: r.message || '打卡失败' });
          }
        });
      },
      fail: function() {
        request.post('/api/check-in/member/' + _this.data.memberId, {}, function(r) {
          if (r && r.success) {
            wx.showToast({ title: '打卡成功' });
            _this.setData({ statusText: '打卡成功！+1积分' });
          } else {
            _this.setData({ statusText: r.message || '打卡失败' });
          }
        });
      }
    });
  }
});
