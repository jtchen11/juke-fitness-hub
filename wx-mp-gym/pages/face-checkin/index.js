var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { statusText: '准备就绪，请面向摄像头', isRegistered: false, memberId: 0 },

  onShow() {
    var u = app.globalData.userInfo || {};
    this.setData({ memberId: u.memberId || 0 });
    // 检查是否已注册人脸
    if (this.data.memberId) {
      var _this = this;
      request.get('/api/face/check', { userId: this.data.memberId }, function(r) {
        if (r && r.registered) _this.setData({ isRegistered: true, statusText: '请点击开始打卡' });
      });
    }
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
      url: (app.globalData.apiBaseUrl || 'http://192.168.10.8:8080') + '/api/face/verify',
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
        // 定位失败时允许打卡
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
