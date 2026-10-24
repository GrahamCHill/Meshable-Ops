export interface MentionChoice {id:string;label:string}
export function mentionTrigger(text:string,caret:number) {
  const match=/(?:^|\s)@([^@\[\]\n]{0,60})$/.exec(text.slice(0,caret))
  return match?{start:caret-match[1].length-1,end:caret,query:match[1]}:undefined
}
const escape=(value:string)=>value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')
export function serializeMentions(text:string,choices:MentionChoice[]) {
  if(!choices.length)return text
  const selected=[...new Map(choices.map(choice=>[choice.label,choice])).values()].sort((a,b)=>b.label.length-a.label.length)
  const pattern=new RegExp('(^|\\s)@('+selected.map(choice=>escape(choice.label)).join('|')+')(?=$|[\\s.,!?;:])','g')
  return text.replace(pattern,(_,before:string,label:string)=>`${before}@[${label.replace(/[\]\r\n]/g,' ')}](user:${selected.find(choice=>choice.label===label)!.id})`)
}
export const mentionPattern=/@\[([^\]\r\n]{1,160})\]\(user:([a-fA-F0-9-]{36})\)/g
