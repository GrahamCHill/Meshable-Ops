import type {CSSProperties} from 'react'
import type {WorkBreakdown,WorkSummary} from '../../api/projects'
import './work-charts.css'

export function WorkCharts({value,showStoryPoints=true,charts,extraMetrics=[]}: {value:WorkSummary;showStoryPoints?:boolean;charts?:{title:string;values:WorkBreakdown[]}[];extraMetrics?:{label:string;value:number}[]}) {
  const colors=['#4c9aff','#6554c0','#36b37e','#ffab00','#00a3bf','#e774bb']
  let offset=0
  const gradient=value.stages.map((stage,index)=>{const start=offset;offset+=stage.count/Math.max(value.total,1)*100;return `${colors[index%colors.length]} ${start}% ${offset}%`}).join(',')
  const chart=(title:string,values:WorkBreakdown[])=>{const max=Math.max(1,...values.map(item=>item.count));return <section className="work-chart"><h3>{title}</h3>{values.length?<ul>{values.map((item,index)=><li key={item.id}><div><span>{item.label}</span><strong>{item.count}</strong></div><div className="work-bar" role="img" aria-label={`${item.label}: ${item.count} tickets${item.completed!==undefined?', '+item.completed+' completed':''}`}><span style={{width:(item.count/max*100)+'%',background:colors[index%colors.length]}}/></div></li>)}</ul>:<p>No tickets yet.</p>}</section>}
  return <div className="page-stack"><div className="work-metrics"><div><strong>{value.total}</strong><span>Total tickets</span></div><div><strong>{value.completed}</strong><span>Completed</span></div><div><strong>{value.total-value.completed}</strong><span>Open tickets</span></div><div><strong>{value.overdue}</strong><span>Overdue</span></div>{showStoryPoints&&<div><strong>{value.storyPoints}</strong><span>Story points</span></div>}</div>
    {!!extraMetrics.length&&<div className="work-metrics">{extraMetrics.map(item=><div key={item.label}><strong>{item.value}</strong><span>{item.label}</span></div>)}</div>}
    <div className="work-chart-grid"><section className="work-chart"><h3>Tickets by column / stage</h3><div className="work-donut-row"><div className="work-donut" role="img" aria-label={`${value.completed} of ${value.total} tickets complete`} style={{background:value.total?'conic-gradient('+gradient+')':'var(--color-surface-2)'} as CSSProperties}><span>{value.total?Math.round(value.completed/value.total*100):0}%<small>complete</small></span></div><ul className="work-legend">{value.stages.map((stage,index)=><li key={stage.id}><i style={{background:colors[index%colors.length]}}/><span>{stage.label}</span><strong>{stage.count}</strong></li>)}</ul></div></section>
      {(charts??[{title:'Assigned tickets per person',values:value.assignees},{title:'Tickets by epic',values:value.epics},{title:'Ticket tags',values:value.tags}]).map(item=><div key={item.title}>{chart(item.title,item.values)}</div>)}
    </div>
  </div>
}
