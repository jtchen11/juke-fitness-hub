var app = getApp();
var request = require('../../utils/request.js');

function pad(n) { return n < 10 ? '0' + n : '' + n; }
function fmtDateTime(s) {
  if (!s) return '';
  var d = new Date(s);
  if (isNaN(d.getTime())) return s;
  return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}
function fmtDate(s) {
  if (!s) return '';
  var d = new Date(s);
  if (isNaN(d.getTime())) return s;
  return (d.getMonth() + 1) + '月' + d.getDate() + '日';
}
function getMemberId() {
  var u = app.globalData.userInfo || wx.getStorageSync('userInfo') || {};
  return u.id || u.memberId || 0;
}

Page({
  data: { activeList: [], endedList: [] },

  onShow() { this.loadData(); },

  loadData() {
    var _this = this;
    var memberId = getMemberId();
    request.get('/api/competitions', { page: 1, size: 50 }, function(r) {
      if (!r || !r.list) return;
      var now = new Date();
      var today = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
      var applyRegs = function(regList) {
        var regs = {};
        (regList || []).forEach(function(reg) { regs[reg.competitionId] = true; });
        _this.renderLists(r.list, regs, today);
      };
      if (memberId) {
        request.get('/api/competition-registrations/member/' + memberId, {}, applyRegs);
      } else {
        applyRegs([]);
      }
    });
  },

  renderLists(list, regs, today) {
    var self = this;
    var active = [];
    var ended = [];
    list.forEach(function(item) {
      if (item.isActive === false) return;
      var deadline = item.deadline ? item.deadline.slice(0, 10) : '';
      var isOpen = item.status === 'open' && (!item.deadline || deadline >= today);
      var registered = !!regs[item.id];
      var card = {
        id: item.id,
        name: item.name,
        description: item.description || '',
        timeText: fmtDateTime(item.startTime) + ' - ' + fmtDateTime(item.endTime),
        deadlineText: item.deadline ? '报名截止 ' + fmtDate(item.deadline) : '',
        enrolled: item.enrolled == null ? 0 : item.enrolled,
        max: item.maxParticipants,
        isOpen: isOpen,
        registered: registered,
        full: item.maxParticipants != null && item.enrolled >= item.maxParticipants
      };
      card.tagText = registered ? '已报名' : (isOpen ? '招募中' : '已结束');
      card.tagClass = registered ? 'tag-registered' : (isOpen ? 'tag-open' : 'tag-ended');
      card.btnText = registered ? '已报名' : (isOpen ? '立即报名' : '已结束');
      card.btnClass = registered ? 'btn-registered' : (isOpen ? 'btn-open' : 'btn-ended');
      card.canRegister = isOpen && !registered;
      card.enrolledText = card.max != null ? card.enrolled + '/' + card.max + ' 人' : card.enrolled + ' 人';
      card.metaText = '已报名 ' + card.enrolledText + (card.deadlineText ? ' · ' + card.deadlineText : '');
      (isOpen ? active : ended).push(card);
    });
    self.setData({ activeList: active, endedList: ended });
  },

  register(e) {
    var _this = this;
    var item = e.currentTarget.dataset.item;
    if (!item || !item.canRegister) return;
    var memberId = getMemberId();
    if (!memberId) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    request.post('/api/competition-registrations', { competitionId: item.id, memberId: memberId }, function(r) {
      if (r && r.success) {
        wx.showToast({ title: '报名成功！', icon: 'success' });
      } else {
        wx.showToast({ title: (r && r.message) || '报名失败', icon: 'none' });
      }
      _this.loadData();
    });
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/competition-detail/index?id=' + e.currentTarget.dataset.id });
  },

  goMyCompetitions() {
    wx.navigateTo({ url: '/pages/my-competitions/index' });
  },

  goBack() { wx.navigateBack(); }
});
