var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: {
    pageMode: 'ready',
    statusText: '准备就绪，请面向摄像头',
    statusIcon: 'ready',
    statusColor: 'gray',
    memberId: 0,
    isRegistered: false,
    actionType: 'start',
    countdown: 3,
    checkinMsg: '',
    isVisitor: false
  },
  onShow() {
    var u = app.globalData.userInfo || {};
    var memberId = u.memberId || 0;
    var level = u.level || "";
    this.setData({ memberId: memberId });
    if (level === "访客" || level === "新用户") {
      this.setData({ pageMode: "unregistered", isVisitor: true, statusText: "请先办理会员卡后使用训练打卡功能", statusIcon: "error", statusColor: "red" });
      return;
    }
    this.checkPermission();
    if (memberId) this.checkRegistered(memberId);
  },
  checkRegistered(memberId) {
    var _this = this;
    request.get('/api/face/check', { userId: String(memberId) }, function(r) {
      if (r && r.registered) {
        _this.setData({ isRegistered: true, pageMode: 'ready', statusText: '请选择上课/下课，点击开始', statusIcon: 'ready', statusColor: 'gray' });
      } else {
        _this.setData({ isRegistered: false, pageMode: 'unregistered', statusText: '尚未注册人脸信息，请先注册', statusIcon: 'error', statusColor: 'red' });
      }
    });
  },
  checkPermission() {
    var _this = this;
    wx.getSetting({
      success: function(res) {
        if (!res.authSetting['scope.camera']) {
          wx.authorize({
            scope: 'scope.camera',
            fail: function() {
              _this.setData({ statusText: '需要摄像头权限才能使用刷脸打卡', statusIcon: 'error', statusColor: 'red' });
              wx.showModal({ title: '提示', content: '需要摄像头权限才能使用刷脸打卡功能', success: function(r) { if (r.confirm) wx.openSetting(); } });
            }
          });
        }
      }
    });
  },
  onCameraError(e) { console.log('摄像头错误:', e.detail); },
  switchAction(e) {
    var act = e.currentTarget.dataset.action;
    this.setData({ actionType: act });
  },
  openCamera(mode) {
    var _this = this;
    wx.chooseImage({
      sourceType: ['camera'],
      count: 1,
      success: function(res) {
        var p = res.tempFilePaths[0];
        var fs = wx.getFileSystemManager();
        var b64 = fs.readFileSync(p, 'base64');
        if (b64.length < 100) { wx.showToast({ title: '拍照失败', icon: 'none' }); return; }
        if (b64.indexOf(',') >= 0) b64 = b64.split(',')[1];
        if (mode === 'register') {
          request.post('/api/face/register', { userId: String(_this.data.memberId), image: b64 }, function(r) {
            if (r && r.success) {
              _this.setData({ isRegistered: true, pageMode: 'ready', statusText: '注册成功，请选择上课/下课', statusIcon: 'success', statusColor: 'green' });
              wx.showToast({ title: '人脸注册成功', icon: 'success' });
            } else {
              _this.setData({ statusText: (r && r.message) || '注册失败，请重试', statusIcon: 'error', statusColor: 'red' });
            }
          });
        } else {
          request.post('/api/face/verify', { userId: String(_this.data.memberId), image: b64 }, function(r) {
            if (r && r.success && r.matched !== false) {
              _this.doAction();
            } else {
              var msg = (r && r.message) || '人脸验证失败';
              if (msg.indexOf('not found') >= 0 || msg.indexOf('未注册') >= 0) {
                msg = '未检测到已注册的人脸信息，请先注册';
                _this.setData({ isRegistered: false, pageMode: 'unregistered' });
              } else if (msg.indexOf('no face') >= 0 || msg.indexOf('未检测到人脸') >= 0) {
                msg = '未检测到人脸，请调整位置';
              } else if (msg.indexOf('score') >= 0 || msg.indexOf('match') >= 0 || msg.indexOf('similarity') >= 0 || r.matched === false) {
                msg = '人脸比对不通过，请确认是本人';
              }
              _this.setData({ statusText: msg, statusIcon: 'error', statusColor: 'red' });
            }
          });
        }
      },
      fail: function() { wx.showToast({ title: '拍照失败', icon: 'none' }); }
    });
  },
  doAction() {
    var _this = this;
    var mid = _this.data.memberId;
    if (_this.data.actionType === 'start') {
      wx.getLocation({
        type: 'wgs84',
        success: function(loc) { request.post('/api/check-in/member/' + mid, { latitude: loc.latitude, longitude: loc.longitude }, function(r) {
          if (r && r.success) {
            _this.setData({ actionType: 'end', pageMode: 'ready', statusText: '正在训练中，点击结束训练打卡', statusIcon: 'success', statusColor: 'green' });
          } else {
            _this.setData({ statusText: (r && r.message) || '签到失败', statusIcon: 'error', statusColor: 'red' });
          }
        }); },
        fail: function() { request.post('/api/check-in/member/' + mid, {}, function(r) {
          if (r && r.success) {
            _this.setData({ actionType: 'end', pageMode: 'ready', statusText: '正在训练中，点击结束训练打卡', statusIcon: 'success', statusColor: 'green' });
          } else {
            _this.setData({ statusText: (r && r.message) || '签到失败', statusIcon: 'error', statusColor: 'red' });
          }
        }); }
      });
    } else {
      request.post('/api/check-in/member/' + mid + '/check-out', {}, function(r) {
        if (r && r.success) {
          _this.handleResult(r, r.message || '打卡完成');
        } else {
          _this.setData({ statusText: (r && r.message) || '签退失败，请重试', statusIcon: 'error', statusColor: 'red' });
        }
      });
    }
  },
  handleResult(r, defaultMsg) {
    if (r && r.success) {
      var msg = r.message || defaultMsg || '打卡成功';
      this.setData({ pageMode: 'success', statusText: msg, statusIcon: 'success', statusColor: 'green', checkinMsg: msg, countdown: 3 });
      this.startCountdown();
    } else {
      this.setData({ statusText: (r && r.message) || '操作失败，请重试', statusIcon: 'error', statusColor: 'red' });
    }
  },
  goBack() { wx.navigateBack(); },

  startCountdown() {
    var _this = this;
    var left = 3;
    var timer = setInterval(function() {
      left--;
      if (left <= 0) {
        clearInterval(timer);
        wx.reLaunch({ url: '/pages/home/index' });
        return;
      }
      _this.setData({ countdown: left });
    }, 1000);
  },
  onRegister() {
    this.setData({ statusText: '正在拍摄...', statusIcon: 'loading', statusColor: 'gray' });
    this.openCamera('register');
  },
  onCapture() {
    this.setData({ statusText: '正在拍摄...', statusIcon: 'loading', statusColor: 'gray' });
    this.openCamera('checkin');
  }
});