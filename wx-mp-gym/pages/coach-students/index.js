var app = getApp();
Page({
  data: { keyword: '', students: [] },
  onShow() { this.loadStudents(); },
  onSearch(e) { this.setData({ keyword: e.detail.value }); this.loadStudents(); },
  loadStudents() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/trainers/' + (app.globalData.userInfo?.trainerId || 0) + '/students', { keyword: this.data.keyword }, function(r) {
      if (r) _this.setData({ students: r.list || r || [] });
    });
  },
  goDetail(e) {
    var id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/coach-students/detail?id=' + id });
  }
});
