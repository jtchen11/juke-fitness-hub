const app = getApp();

const MEMBER_TABS = [
  { pagePath: "pages/home/index", text: "首页", iconPath: "/images/tab_home.png", selectedIconPath: "/images/tab_home_hl.png" },
  { pagePath: "pages/courses/index", text: "课程", iconPath: "/images/tab_course.png", selectedIconPath: "/images/tab_course_hl.png" },
  { pagePath: "pages/ai-chat/index", text: "AI", iconPath: "/images/tab_ai.png", selectedIconPath: "/images/tab_ai_hl.png" },
  { pagePath: "pages/mine/index", text: "我的", iconPath: "/images/tab_mine.png", selectedIconPath: "/images/tab_mine_hl.png" }
];

const COACH_TABS = [
  { pagePath: "pages/coach-home/index", text: "课表", iconPath: "/images/tab_home.png", selectedIconPath: "/images/tab_home_hl.png" },
  { pagePath: "pages/coach-appointments/index", text: "预约管理", iconPath: "/images/tab_booking.png", selectedIconPath: "/images/tab_booking_hl.png" },
  { pagePath: "pages/coach-students/index", text: "学员", iconPath: "/images/tab_course.png", selectedIconPath: "/images/tab_course_hl.png" },
  { pagePath: "pages/coach-mine/index", text: "我的", iconPath: "/images/tab_mine.png", selectedIconPath: "/images/tab_mine_hl.png" }
];

Component({
  data: { mode: "member", tabs: MEMBER_TABS, selectedIndex: 0 },
  lifetimes: {
    attached() { this.updateTabs(); }
  },
  pageLifetimes: {
    show() { this.updateTabs(); }
  },
  methods: {
    updateTabs() {
      const mode = wx.getStorageSync("appMode") || "member";
      const tabs = mode === "coach" ? COACH_TABS : MEMBER_TABS;
      const pages = getCurrentPages();
      const currentPath = pages.length > 0 ? pages[pages.length - 1].route : "";
      let selectedIndex = 0;
      tabs.forEach((t, i) => { if (t.pagePath === currentPath) selectedIndex = i; });
      this.setData({ mode, tabs, selectedIndex });
    },
    onTabTap(e) {
      const index = e.currentTarget.dataset.index;
      const tab = this.data.tabs[index];
      if (!tab) return;
      const pages = getCurrentPages();
      const currentPath = pages.length > 0 ? pages[pages.length - 1].route : "";
      if (tab.pagePath === currentPath) return;
      this.setData({ selectedIndex: index });
      wx.reLaunch({ url: "/" + tab.pagePath });
    }
  }
});