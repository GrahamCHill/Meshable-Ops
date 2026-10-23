function readVar(name: string, fallback: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

export function applySupersetTheme(doc: Document, mode: 'dark' | 'light') {
  const styleId = 'superset-embed-app-theme'

  if (mode === 'light') {
    doc.getElementById(styleId)?.remove()
    return
  }

  const bg = readVar('--color-bg', '#0f1117')
  const surface = readVar('--color-surface', '#1a1d27')
  const surface2 = readVar('--color-surface-2', '#22263a')
  const border = readVar('--color-border', 'rgba(255,255,255,0.08)')
  const text = readVar('--color-text', '#e8eaf0')
  const textSecondary = readVar('--color-text-secondary', '#8b90a8')
  const accent = readVar('--color-accent', '#6c6ef5')

  let style = doc.getElementById(styleId) as HTMLStyleElement | null
  if (!style) {
    style = doc.createElement('style')
    style.id = styleId
    doc.head?.appendChild(style)
  }

  style.textContent = `
    :root {
      color-scheme: dark;
      --app-embed-bg: ${bg};
      --app-embed-surface: ${surface};
      --app-embed-surface-2: ${surface2};
      --app-embed-border: ${border};
      --app-embed-text: ${text};
      --app-embed-text-secondary: ${textSecondary};
      --app-embed-accent: ${accent};
    }

    html, body, #app, .dashboard, .standalone, .standalone-dashboard,
    .dashboard-container, .grid-container, .dashboard-content,
    .dashboard--editing-view, .dashboard--view-mode, .fullscreen,
    .chart-slice, .slice_container, .chart-holder {
      background: var(--app-embed-bg) !important;
      color: var(--app-embed-text) !important;
    }

    .dashboard-header, .sticky-header, .header, .navbar, .top-header,
    .dashboard-component-header, .dashboard-header-container,
    .css-1d3w5wq, .ant-layout-header, [data-test="dashboard-header"],
    [data-test="dashboard-filters-panel"], [data-test="filter-bar"],
    #app > div > div.css-1bl3vr5 > div > div,
    #app > div > div.css-1bl3vr5 > div > div *,
    [data-test="filter-box"], [data-test="filter-control-op-container"],
    .native-filter-panel, .filter-bar, .filter-bar-container, .filter-item, .filter-control,
    .ant-card, .ant-popover-inner, .ant-dropdown-menu, .ant-menu,
    .ant-modal-content, .ant-select-dropdown, .ant-tooltip-inner,
    .ant-picker-panel-container, .ant-tabs-nav, .ant-tabs-content,
    .ant-drawer-content, .ant-notification-notice, .ant-collapse,
    .ant-collapse-content, .ant-collapse-item, .css-1wxaqej, .css-k008qs,
    .css-1bl3vr5, .css-vhf0sc, .css-vhf0sc > div, .css-qimrd8, .css-qimrd8 > div, .css-qimrd8 > div > div,
    .css-qimrd8 > div > div > div, .css-qimrd8 > div > div > div > div,
    .css-3i11gm,
    #loginbox, #loginbox > div, #loginbox form,
    #main-menu > div > div.ant-col.ant-col-xs-24.ant-col-md-8 > div,
    [data-test="dashboard-filters-panel"] .ant-card,
    [data-test="dashboard-filters-panel"] .ant-card-body,
    [data-test="dashboard-filters-panel"] .ant-collapse-content-box,
    [data-test="dashboard-filters-panel"] .native-filter-panel,
    [data-test="dashboard-filters-panel"] .filter-control,
    [data-test="dashboard-filters-panel"] .filter-item,
    .dashboard-filters-panel,
    .dashboard-filters-panel *,
    .native-filter-panel,
    .native-filter-panel *,
    .loading, .loading-container, .loading-component, .loading-slice, .loading-content,
    .ant-spin-container, .ant-spin-nested-loading, .ant-skeleton, .ant-skeleton-content,
    .ant-skeleton-title, .ant-skeleton-paragraph, .ant-progress, .chart-loading,
    [id$='-panel-GRID_ID'] > div, [id$='-panel-GRID_ID'] > div > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(1) > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(1) > div > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(1) > div > div > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(1) > div > div > div > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(1) > div > div > div > div > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(2) > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(2) > div > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(2) > div > div > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(2) > div > div > div > div,
    [id$='-panel-GRID_ID'] > div > div > div:nth-child(2) > div > div > div > div > div {
      background: var(--app-embed-surface) !important;
      color: var(--app-embed-text) !important;
      border-color: var(--app-embed-border) !important;
      box-shadow: none !important;
    }

    .ant-input, .ant-input-affix-wrapper, .ant-select-selector,
    .ant-picker, .ant-btn:not(.ant-btn-primary), textarea, input, select,
    .Select-control, .Select-menu-outer, .Select-placeholder,
    .Select-input > input, .filter-item input, .filter-item select,
    .ant-collapse-header, .native-filter-panel input, .native-filter-panel select,
    .dashboard-filters-panel input, .dashboard-filters-panel select,
    #loginbox input, #loginbox select, #loginbox textarea {
      background: var(--app-embed-surface-2) !important;
      color: var(--app-embed-text) !important;
      border-color: var(--app-embed-border) !important;
    }

    .ant-btn-primary, .css-1d3w5wq .ant-btn-primary, button[type="button"].ant-btn-primary {
      background: var(--app-embed-surface-2) !important;
      border: 0 !important;
      border-radius: 0 !important;
      box-shadow: none !important;
      color: var(--app-embed-text) !important;
    }

    .ant-typography, .ant-tabs-tab, .ant-dropdown-menu-item,
    .ant-menu-item, .ant-select-item, .ant-modal-title, .ant-form-item-label > label,
    .header-title, .dashboard-header span, .dashboard-header div, label, p, h1, h2, h3, h4, h5, h6,
    .filter-item-label, .ant-breadcrumb, .ant-breadcrumb-link, .ant-page-header-heading-title,
    .nav, .navbar *, .header *, .ant-dropdown-menu-title-content,
    .ant-dropdown-menu-submenu-title, .ant-dropdown-menu-item > span,
    .ant-dropdown-menu-item a, .ant-dropdown-menu-item button,
    .ant-select-selection-item, .ant-select-selection-placeholder,
    .ant-select-item-option-content, .ant-empty-description,
    .ant-collapse-header-text, .ant-tooltip-inner, .ant-popover-inner-content,
    .ant-btn > span, .ant-tag, .ant-badge-status-text,
    .css-1bl3vr5 span, .css-1bl3vr5 p, .css-1bl3vr5 small,
    .css-vhf0sc span, .css-vhf0sc p, .css-vhf0sc small {
      color: var(--app-embed-text) !important;
    }

    .ant-empty-description, .ant-form-item-explain, .ant-typography-secondary,
    .ant-select-item-option-content, .dashboard-markdown p, .dashboard-markdown span,
    .filter-item span, .filter-item small, .ant-input::placeholder, input::placeholder, textarea::placeholder,
    #loginbox .help-block, #loginbox small, #loginbox p,
    .ant-select-selection-placeholder, .ant-empty-normal,
    .ant-empty-normal .ant-empty-description, .ant-form-item-extra {
      color: var(--app-embed-text-secondary) !important;
    }

    .ant-tabs-tab-btn, .ant-breadcrumb a, .ant-dropdown-menu-item a, .ant-menu-item a,
    a, .filter-item a, .navbar a, .header a, #loginbox a {
      color: var(--app-embed-accent) !important;
    }

    .ant-dropdown-menu-item:hover,
    .ant-dropdown-menu-item-active,
    .ant-dropdown-menu-submenu-title:hover,
    .ant-dropdown-menu-item-selected {
      background: var(--app-embed-surface-2) !important;
      color: var(--app-embed-text) !important;
    }

    .anticon, .anticon svg, .anticon path,
    .ant-select-arrow, .ant-select-arrow svg, .ant-select-arrow path,
    .ant-collapse-arrow, .ant-collapse-arrow svg, .ant-collapse-arrow path,
    .ant-dropdown-menu-submenu-expand-icon, .ant-dropdown-menu-submenu-expand-icon svg,
    .ant-dropdown-menu-submenu-expand-icon path,
    .ant-breadcrumb-separator, .ant-picker-suffix, .ant-picker-suffix svg, .ant-picker-suffix path,
    [role="img"], [role="img"] svg, [role="img"] path {
      color: var(--app-embed-text-secondary) !important;
      fill: currentColor !important;
      stroke: currentColor !important;
    }

    #app > div > div.css-1bl3vr5 > div > div button,
    #app > div > div.css-1bl3vr5 > div > div span,
    #app > div > div.css-1bl3vr5 > div > div p,
    #app > div > div.css-1bl3vr5 > div > div div,
    #app > div > div.css-1bl3vr5 > div > div svg,
    #app > div > div.css-1bl3vr5 > div > div path {
      color: var(--app-embed-text) !important;
      fill: currentColor !important;
      stroke: currentColor !important;
    }

    .ant-divider, .ant-tabs-ink-bar, .ant-card-bordered, .resizable-container,
    .grid-row, .dashboard-component-chart-holder, .dashboard-header,
    .sticky-header, .filter-bar, .native-filter-panel, .ant-collapse,
    .ant-collapse-item, .ant-collapse-content-box, .css-1bl3vr5,
    .css-qimrd8, .ant-table-thead > tr > th, thead th {
      border-color: var(--app-embed-border) !important;
    }

    .ant-table-thead > tr > th, thead th,
    .ant-table-header, .tablethead, .tableHeader, .header-cell {
      background: var(--app-embed-surface-2) !important;
      color: var(--app-embed-text) !important;
      border-color: var(--app-embed-border) !important;
    }

    .ant-table-tbody > tr > td,
    tbody tr > td,
    .table tbody tr > td {
      background: var(--app-embed-surface) !important;
      color: var(--app-embed-text) !important;
      border-color: var(--app-embed-border) !important;
    }

    .ant-table-tbody > tr:nth-child(odd) > td,
    tbody tr:nth-child(odd) > td,
    .table tbody tr:nth-child(odd) > td {
      background: color-mix(in srgb, var(--app-embed-surface) 88%, #ffffff 12%) !important;
    }

    .ant-table-tbody > tr:nth-child(even) > td,
    tbody tr:nth-child(even) > td,
    .table tbody tr:nth-child(even) > td {
      background: var(--app-embed-surface) !important;
    }

    .ant-spin-dot-item, .ant-progress-bg {
      background: var(--app-embed-accent) !important;
    }

    .ant-skeleton-title, .ant-skeleton-paragraph > li {
      background: color-mix(in srgb, var(--app-embed-surface-2) 82%, #ffffff 18%) !important;
    }

    svg text, .nvd3 text, .recharts-text, .chart-container text,
    .axis text, .legend text {
      fill: var(--app-embed-text-secondary) !important;
    }
  `
}
