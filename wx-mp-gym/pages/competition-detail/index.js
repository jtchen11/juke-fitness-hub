var app = getApp();
var request = require('../../utils/request.js');

function pad(n) { return n < 10 ? '0' + n : '' + n; }
function fmtDate(s) {
  if (!s) return '';
  var d = new Date(s);
  if (isNaN(d.getTime())) return s;
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}
function getMemberId() {
  var u = app.globalData.userInfo || wx.getStorageSync('userInfo') || {};
  return u.id || u.memberId || 0;
}

Page({
  data: { comp: {}, statusText: '', registered: false },

  onLoad(e) { this.loadComp(e.id); },

  loadComp(id) {
    var _this = this;
    request.get('/api/competitions/' + id, {}, function(r) {
      if (!r) return;
      var m = { open: '招募中', active: '进行中', closed: '已结束', ended: '已结束', cancelled: '已取消' };
      r.startDate = fmtDate(r.startTime);
      r.endDate = fmtDate(r.endTime);
      r.deadlineText = r.deadline ? '报名截止 ' + fmtDate(r.deadline) : '';
      r.enrolledText = (r.maxParticipants != null ? r.enrolled + '/' + r.maxParticipants : r.enrolled) + ' 人';
      r.hasPrize = (r.championPoints > 0 || r.runnerUpPoints > 0 || r.thirdPlacePoints > 0 || r.participationPoints > 0);
      _this.setData({ comp: r, statusText: m[r.status] || r.status });
      _this.checkRegistered(id);
    });
  },

  checkRegistered(competitionId) {
    var _this = this;
    var memberId = getMemberId();
    if (!memberId) return;
    request.get('/api/competition-registrations/member/' + memberId, {}, function(regs) {
      var done = (regs || []).some(function(reg) {
        return Number(reg.competitionId) === Number(competitionId) && reg.status === 'registered';
      });
      if (done) _this.setData({ registered: true });
    });
  },

  onRegister() {
    var _this = this;
    var memberId = getMemberId();
    if (!memberId) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    request.post('/api/competition-registrations', { competitionId: this.data.comp.id, memberId: memberId }, function(r) {
      if (r && r.success) {
        wx.showToast({ title: '报名成功！', icon: 'success' });
        _this.setData({ registered: true });
      } else {
        wx.showToast({ title: (r && r.message) || '报名失败', icon: 'none' });
      }
    });
  },

  goBack() { wx.navigateBack(); }
});
