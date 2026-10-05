// 密级展示配置：颜色随密级加深，编辑/查看/审批页显著显示
export const CLASSIFICATIONS = [
  { key: 'PUBLIC', label: '公开', level: 1, color: '#15803d', bg: '#dcfce7' },
  { key: 'INTERNAL', label: '内部', level: 2, color: '#1d4ed8', bg: '#dbeafe' },
  { key: 'SECRET', label: '机密', level: 3, color: '#b45309', bg: '#fef3c7' },
  { key: 'TOP_SECRET', label: '绝密', level: 4, color: '#b91c1c', bg: '#fee2e2' }
];

export function classificationOf(key) {
  return CLASSIFICATIONS.find((c) => c.key === key) || CLASSIFICATIONS[0];
}

export const STATUS_LABELS = {
  DRAFT: '草稿',
  PENDING: '待审批',
  APPROVED: '已发布',
  REJECTED: '已驳回'
};

export const ROLE_LABELS = {
  RECORDER: '记录员',
  APPROVER: '负责人',
  ADMIN: '管理员',
  STAFF: '员工'
};
