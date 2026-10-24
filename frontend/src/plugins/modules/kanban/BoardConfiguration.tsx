import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {projectsApi} from '../../../api/projects'
import {Button} from '../../../components/ui'
import {Modal} from '../../../components/ui/Modal'
import {usePageDraft} from '../../../hooks/usePageDraft'
import {BoardProjectFields} from './BoardProjectFields'
import {ColumnSettings} from './ColumnSettings'
import {EpicSettings} from './EpicSettings'
import {SprintFields} from './SprintFields'
import {WorkflowGraph,emptyWorkflow} from './WorkflowGraph'
import {ProjectEstimationPanel} from './ProjectEstimationPanel'
import type {Person} from '../../../api/projects'
import {boardsApi,type BoardInput} from './api'

export function BoardConfiguration({id,board,people,busy,error,onSave,onClose}: {id?:string;board:BoardInput;people:Person[];busy:boolean;error?:string;onSave:(next:BoardInput)=>void;onClose:()=>void}) {
  const [draft,setDraft,clear]=usePageDraft('board-config:'+(id??'new'),()=>({name:board.name,projectId:board.projectId,teamIds:board.teamIds,columns:board.content.columns,sprints:board.content.sprints??[],epics:board.content.epics??[],workflow:board.content.workflow??emptyWorkflow}))
  const [tab,setTab]=useState(id?'columns':'details')
  const projectPeople=useQuery({queryKey:['project-members',draft.projectId],queryFn:()=>projectsApi.members(draft.projectId!),enabled:!!draft.projectId&&(!id||draft.projectId!==board.projectId)})
  const checkingScope=!id||draft.projectId!==board.projectId
  const creationAccess=useQuery({queryKey:['kanban-create-access',draft.projectId],queryFn:()=>boardsApi.permissions(draft.projectId??undefined),enabled:checkingScope})
  const allowed=!checkingScope||creationAccess.data?.canCreate===true
  const next={...board,name:draft.name,projectId:draft.projectId,teamIds:draft.teamIds,content:{...board.content,columns:draft.columns,sprints:draft.sprints,epics:draft.epics,workflow:draft.workflow??emptyWorkflow}}
  return <Modal title={id?'Board configuration':'Create board'} className="project-dialog kanban-config-dialog" onClose={onClose} busy={busy}>
    <nav className="workspace-tabs" aria-label="Board configuration sections">{[['details','Details & access'],['columns','Columns & permissions'],['workflow','Workflow graph'],['epics','Epics'],['sprints','Sprints'],...(draft.projectId?[['estimation','Estimation']]:[])].map(([key,label])=><button key={key} type="button" aria-current={tab===key?'page':undefined} onClick={()=>setTab(key)}>{label}</button>)}</nav>
    <form className="page-stack" onSubmit={e=>{e.preventDefault();if(allowed)onSave(next)}}><fieldset className="page-stack" disabled={busy}>
      {tab==='details'&&<><label className="form-field">Board name<input autoFocus required maxLength={120} value={draft.name} onChange={e=>setDraft({...draft,name:e.target.value})}/></label><BoardProjectFields draft={next} disabled={busy} onChange={value=>setDraft({...draft,projectId:value.projectId,teamIds:value.teamIds})}/></>}
      {tab==='columns'&&<ColumnSettings columns={draft.columns} cards={board.content.cards} people={projectPeople.data??people} onChange={columns=>{const ids=new Set(columns.map(column=>column.id)),workflow=draft.workflow??emptyWorkflow;setDraft({...draft,columns,workflow:{...workflow,transitions:workflow.transitions.filter(item=>ids.has(item.source)&&ids.has(item.target)),positions:Object.fromEntries(Object.entries(workflow.positions).filter(([id])=>ids.has(id)))}})}}/>}
      {tab==='workflow'&&<WorkflowGraph columns={draft.columns} value={draft.workflow??emptyWorkflow} people={projectPeople.data??people} disabled={busy} onChange={workflow=>setDraft({...draft,workflow})}/>}
      {tab==='estimation'&&draft.projectId&&<ProjectEstimationPanel projectId={draft.projectId}/>}
      {tab==='epics'&&<EpicSettings epics={draft.epics} cards={board.content.cards} onChange={epics=>setDraft({...draft,epics})}/>}
      {tab==='sprints'&&<SprintFields value={draft.sprints} onChange={sprints=>setDraft({...draft,sprints})} disabled={busy}/>}
    </fieldset>{checkingScope&&!allowed&&<p role="status">{creationAccess.isLoading?'Checking creation permissions…':'You need creation permission and, for a linked board, a manager or team lead role in that project. Choose another project or a private board.'}</p>}{error&&<p role="alert">{error}</p>}<div className="inline-actions"><Button type="submit" loading={busy} disabled={!allowed||!draft.name.trim()||draft.columns.some(column=>!column.title.trim())}>{id?'Save configuration':'Create board'}</Button><Button type="button" variant="ghost" disabled={busy} onClick={onClose}>Close</Button><Button type="button" variant="ghost" disabled={busy} onClick={clear}>Reset draft</Button></div></form>
  </Modal>
}
