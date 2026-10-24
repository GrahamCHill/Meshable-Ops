import type {BoardInput} from './api'
import type {Person} from '../../../api/projects'
import type {WorkSummary,WorkBreakdown} from '../../../components/projects/WorkCharts'

export function boardSummary(board:BoardInput,people:Person[]):WorkSummary {
  const cards=board.content.cards
  const group=(key:(card:typeof cards[number])=>string,label:(id:string)=>string):WorkBreakdown[]=>{
    const values=new Map<string,WorkBreakdown>()
    for(const card of cards){const id=key(card),entry=values.get(id)??{id,label:label(id),count:0,completed:0};entry.count++;entry.completed=(entry.completed??0)+(card.status==='DONE'?1:0);values.set(id,entry)}
    return [...values.values()].sort((a,b)=>b.count-a.count)
  }
  const tags=new Map<string,number>();cards.forEach(card=>card.tags?.forEach(tag=>tags.set(tag,(tags.get(tag)??0)+1)))
  return {total:cards.length,completed:cards.filter(card=>card.status==='DONE').length,overdue:cards.filter(card=>card.status!=='DONE'&&card.dueDate&&card.dueDate<new Date().toISOString().slice(0,10)).length,storyPoints:cards.reduce((sum,card)=>sum+(card.storyPoints??0),0),
    stages:group(card=>card.columnId,id=>board.content.columns.find(column=>column.id===id)?.title??'Unknown'),assignees:group(card=>card.assigneeId??'',id=>people.find(person=>person.id===id)?.name??'Unassigned'),epics:group(card=>card.epicId??'',id=>board.content.epics?.find(epic=>epic.id===id)?.name??'No epic'),tags:[...tags].map(([id,count])=>({id,label:id,count}))}
}
