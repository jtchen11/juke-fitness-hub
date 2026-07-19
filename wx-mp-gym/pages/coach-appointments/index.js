var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { statusFilter: '', appointments: [] },
  onShow() { this.loadAppointments(); },
  filterStatus(e) { this.setData({ statusFilter: e.currentTarget.dataset.status }); this.loadAppointments(); },
  loadAppointments() {
    var _this = this;
    var url = (app.globalData.apiBaseUrl || 'http://192.168.10.8:8080') + '/api/trainers/' + (app.globalData.userInfo?.trainerId || 0) + '/appointments?status=' + this.data.statusFilter;
    wx.request({
      url: url,
      success: function(r) { if (r.data) _this.setData({ appointments: r.data }); },
      fail: function() {}
    });
  },
  onCheckIn(e) {
    var id = e.currentTarget.dataset.id;
    var _this = this;
    wx.showModal({
      title: '教练端打卡',
      content: '请确认会员已到场，是否开始上课？',
      success: function(res) {
        if (res.confirm) {
          // 直接标记为已完成（人脸模块可后续对接）
          wx.showLoading({ title: '正在打卡...' });
          request.patch('/api/personal-training/' + id + '/status', { status: 'completed' }, function(r) {
            wx.hideLoading();
            if (r && r.success) {
              wx.showToast({ title: '打卡成功', icon: 'success' });
              _this.loadAppointments();
            } else {
              wx.showToast({ title: r.message || '打卡失败', icon: 'none' });
            }
          });
        }
      }
    });
  },
  onCancel(e) {
    var id = e.currentTarget.dataset.id;
    var _this = this;
    wx.showModal({
      title: '确认取消',
      content: '确定取消该预约吗？',
      success: function(r) {
        if (r.confirm) {
          request.patch('/api/personal-training/' + id + '/status', { status: 'cancelled' }, function(r) {
            if (r && r.success) { wx.showToast({ title: '已取消', icon: 'success' }); _this.loadAppointments(); }
            else { wx.showToast({ title: r.message || '操作失败', icon: 'none' }); }
          });
        }
      }
    });
  }
});
