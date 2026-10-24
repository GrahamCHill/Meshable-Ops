import {useState} from 'react'
import {Button} from '../../../components/ui'
import {Modal} from '../../../components/ui/Modal'
import {usePageDraft} from '../../../hooks/usePageDraft'
import {BoardProjectFields} from './BoardProjectFields'
import {ColumnSettings} from './ColumnSettings'
import {EpicSettings} from './EpicSettings'
import {SprintFields} from './SprintFields'
import type {Person} from '../../../api/projects'
import type {BoardInput} from './api'

export function BoardConfiguration({id,board,people,busy,error,onSave,onClose}: {id?:string;board:BoardInput;people:Person[];busy:boolean;error?:string;onSave:(next:BoardInput)=>void;onClose:()=>void}) {
  const [draft,setDraft,clear]=usePageDraft('board-config:'+(id??'new'),()=>({name:board.name,projectId:board.projectId,teamIds:board.teamIds,columns:board.content.columns,sprints:board.content.sprints??[],epics:board.content.epics??[]}))
  const [tab,setTab]=useState(id?'columns':'details')
  const next={...board,name:draft.name,projectId:draft.projectId,teamIds:draft.teamIds,content:{...board.content,columns:draft.columns,sprints:draft.sprints,epics:draft.epics}}
  return <Modal title={id?'Board configuration':'Create board'} onClose={onClose} busy={busy}>
    <nav className="workspace-tabs" aria-label="Board configuration sections">{[['details','Details & access'],['columns','Columns & permissions'],['epics','Epics'],['sprints','Sprints']].map(([key,label])=><button key={key} type="button" aria-current={tab===key?'page':undefined} onClick={()=>setTab(key)}>{label}</button>)}</nav>
    <form className="page-stack" onSubmit={e=>{e.preventDefault();onSave(next)}}><fieldset className="page-stack" disabled={busy}>
      {tab==='details'&&<><label className="form-field">Board name<input autoFocus required maxLength={120} value={draft.name} onChange={e=>setDraft({...draft,name:e.target.value})}/></label><BoardProjectFields draft={next} disabled={busy} onChange={value=>setDraft({...draft,projectId:value.projectId,teamIds:value.teamIds})}/></>}
      {tab==='columns'&&<ColumnSettings columns={draft.columns} cards={board.content.cards} people={people} onChange={columns=>setDraft({...draft,columns})}/>}
      {tab==='epics'&&<EpicSettings epics={draft.epics} cards={board.content.cards} onChange={epics=>setDraft({...draft,epics})}/>}
      {tab==='sprints'&&<SprintFields value={draft.sprints} onChange={sprints=>setDraft({...draft,sprints})} disabled={busy}/>}
    </fieldset>{error&&<p role="alert">{error}</p>}<div className="inline-actions"><Button type="submit" loading={busy} disabled={!draft.name.trim()||draft.columns.some(column=>!column.title.trim())}>{id?'Save configuration':'Create board'}</Button><Button type="button" variant="ghost" disabled={busy} onClick={onClose}>Close</Button><Button type="button" variant="ghost" disabled={busy} onClick={clear}>Reset draft</Button></div></form>
  </Modal>
}
