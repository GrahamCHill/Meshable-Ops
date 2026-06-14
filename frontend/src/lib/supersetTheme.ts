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
    [data-test="filter-box"], [data-test="filter-control-op-container"],
    .native-filter-panel, .filter-bar, .filter-bar-container, .filter-item, .filter-control,
    .ant-card, .ant-popover-inner, .ant-dropdown-menu, .ant-menu,
    .ant-modal-content, .ant-select-dropdown, .ant-tooltip-inner,
    .ant-picker-panel-container, .ant-tabs-nav, .ant-tabs-content,
    .ant-drawer-content, .ant-notification-notice, .ant-collapse,
    .ant-collapse-content, .ant-collapse-item, .css-1wxaqej, .css-k008qs,
    .css-1bl3vr5, .css-qimrd8, .css-qimrd8 > div, .css-qimrd8 > div > div,
    .css-qimrd8 > div > div > div, .css-qimrd8 > div > div > div > div,
    .css-3i11gm,
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
    .dashboard-filters-panel input, .dashboard-filters-panel select {
      background: var(--app-embed-surface-2) !important;
      color: var(--app-embed-text) !important;
      border-color: var(--app-embed-border) !important;
    }

    .ant-btn-primary, .css-1d3w5wq .ant-btn-primary, button[type="button"].ant-btn-primary {
      background: var(--app-embed-accent) !important;
      border-color: var(--app-embed-accent) !important;
      color: #fff !important;
    }

    .ant-typography, .ant-tabs-tab, .ant-dropdown-menu-item,
    .ant-menu-item, .ant-select-item, .ant-modal-title, .ant-form-item-label > label,
    .header-title, .dashboard-header span, .dashboard-header div, label, p, h1, h2, h3, h4, h5, h6,
    .filter-item-label, .ant-breadcrumb, .ant-breadcrumb-link, .ant-page-header-heading-title,
    .nav, .navbar *, .header * {
      color: var(--app-embed-text) !important;
    }

    .ant-empty-description, .ant-form-item-explain, .ant-typography-secondary,
    .ant-select-item-option-content, .dashboard-markdown p, .dashboard-markdown span,
    .filter-item span, .filter-item small, .ant-input::placeholder, input::placeholder, textarea::placeholder {
      color: var(--app-embed-text-secondary) !important;
    }

    .ant-tabs-tab-btn, .ant-breadcrumb a, .ant-dropdown-menu-item a, .ant-menu-item a,
    a, .filter-item a, .navbar a, .header a {
      color: var(--app-embed-accent) !important;
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

    svg text, .nvd3 text, .recharts-text, .chart-container text,
    .axis text, .legend text {
      fill: var(--app-embed-text-secondary) !important;
    }
  `
}
