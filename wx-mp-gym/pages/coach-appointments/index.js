var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { statusFilter: '', appointments: [] },
  onShow() { this.loadAppointments(); },
  filterStatus(e) { this.setData({ statusFilter: e.currentTarget.dataset.status }); this.loadAppointments(); },
  loadAppointments() {
    var _this = this;
    var params = {};
    if (this.data.statusFilter) params.status = this.data.statusFilter;
    var trainerId = (app.globalData.userInfo && app.globalData.userInfo.trainerId) || 0;
    request.get('/api/trainers/' + trainerId + '/appointments', params, function(r) {
      if (r) {
        var list = r.list || r || [];
        // 对缺失 memberName 的记录做兜底显示
        list.forEach(function(item) {
          if (!item.memberName || item.memberName.indexOf('#') >= 0) {
            item.memberName = item.memberNameDisplay || item.memberName || '会员';
          }
        });
        _this.setData({ appointments: list });
      }
    });
  },
  onCheckIn(e) {
    var id = e.currentTarget.dataset.id;
    var memberId = e.currentTarget.dataset.memberid;
    var _this = this;
    // 1. 拍照获取会员正脸照片
    wx.chooseImage({
      sourceType: ['camera'],
      count: 1,
      success: function(res) {
        var tempPath = res.tempFilePaths[0];
        wx.showLoading({ title: '正在验证人脸...' });
        // 2. 读取图片为 base64
        var fs = wx.getFileSystemManager();
        var base64 = fs.readFileSync(tempPath, 'base64');
        if (base64.indexOf(',') >= 0) base64 = base64.split(',')[1];
        // 3. 调用人脸验证接口
        request.post('/api/face/verify', { userId: String(memberId), image: base64 }, function(r) {
          if (r && r.success) {
            wx.showLoading({ title: '验证通过，正在打卡...' });
            // 4. 人脸验证通过，自动打卡
            request.post('/api/check-in/pt/coach/' + id + '?memberId=' + memberId + '&action=start', null, function(r2) {
              wx.hideLoading();
              if (r2 && r2.success) {
                wx.showToast({ title: '打卡成功', icon: 'success' });
                _this.loadAppointments();
              } else {
                wx.showToast({ title: (r2 && r2.message) || '打卡失败', icon: 'none' });
              }
            });
          } else {
            wx.hideLoading();
            wx.showToast({ title: '人脸验证失败，请确认是会员本人', icon: 'none' });
          }
        });
      },
      fail: function() {
        wx.showToast({ title: '拍照取消或失败', icon: 'none' });
      }
    });
  },
  goDetail(e) {
    var id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/coach-appointments/detail?id=' + id });
  },
  onCancel(e) {
    var id = e.currentTarget.dataset.id;
    var _this = this;
    wx.showModal({
      title: '确认取消',
      content: '确定取消该预约吗？',
      success: function(r) {
        if (r.confirm) {
          request.post('/api/personal-trainings/' + id + '/cancel', {}, function(r) {
            if (r && r.success) { wx.showToast({ title: '已取消', icon: 'success' }); _this.loadAppointments(); }
            else { wx.showToast({ title: r.message || '操作失败', icon: 'none' }); }
          });
        }
      }
    });
  }
});
