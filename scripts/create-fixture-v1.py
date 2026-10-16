#!/usr/bin/env python3
"""Gera SOMENTE dados sintéticos. Não acessa banco instalado nem importa dados de usuários."""
from pathlib import Path
import json, sqlite3, hashlib, argparse
from datetime import datetime, timezone
ROOT = Path(__file__).resolve().parent.parent

def fixture():
    def klass(ident, name):
        return dict(id=ident,name=name,stage='Médio',subject='História fictícia',year=2026,shift='Matutino',schedule='0,0,2,1,0,0,0',duration=50,scale=10.0,low=5.0,high=7.0,countJustified=True,startPresent=False)
    def student(ident,person,cls,name,number,joined='2026-02-01',left=None,status='Ativo'):
        return dict(id=ident,personId=person,classId=cls,name=name,number=number,joined=joined,left=left,status=status,notes='Observação pedagógica sintética — ç/á/é')
    c1,c2=klass('classe-alfa','Turma fictícia Alfa'),klass('classe-beta','Turma fictícia Beta')
    students=[student('alfa','p-alfa','classe-alfa','Estudante Fictício Alfa',1),student('beta-origem','p-beta','classe-alfa','Estudante Fictício Beta',2,left='2026-05-31',status='Transferido'),student('beta-destino','p-beta','classe-beta','Estudante Fictício Beta',1,joined='2026-06-01'),student('gama','p-gama','classe-alfa','Estudante Fictício Gama',3,joined='2026-03-01')]
    periods=[('2026-01-01','2026-03-31'),('2026-04-01','2026-06-30'),('2026-07-01','2026-09-30'),('2026-10-01','2026-12-31')]
    terms=[dict(classId=c['id'],number=i+1,start=a,end=b) for c in [c1,c2] for i,(a,b) in enumerate(periods)]
    lessons=[dict(id='aula-1',classId='classe-alfa',date='2026-02-04',ordinal=1,state='REALIZADA',note='Aula dupla fictícia'),dict(id='aula-2',classId='classe-alfa',date='2026-02-04',ordinal=2,state='REALIZADA',note=''),dict(id='aula-cancelada',classId='classe-alfa',date='2026-02-05',ordinal=1,state='CANCELADA',note='Feriado fictício'),dict(id='aula-nr',classId='classe-alfa',date='2026-03-04',ordinal=1,state='REALIZADA',note=''),dict(id='aula-prevista',classId='classe-alfa',date='2026-03-05',ordinal=1,state='PREVISTA',note='')]
    marks=[dict(lessonId=l,studentId=s,mark=m) for l,s,m in [('aula-1','alfa','P'),('aula-2','alfa','F'),('aula-1','beta-origem','FJ'),('aula-2','beta-origem','P'),('aula-cancelada','alfa','F'),('aula-nr','gama','NR')]]
    activities=[dict(id='atividade-1',classId='classe-alfa',name='Avaliação fictícia',type='Prova',date='2026-02-04',term=1,maximum=10.0,description='Somente teste',notes=''),dict(id='atividade-2',classId='classe-alfa',name='Trabalho fictício',type='Trabalho',date='2026-08-20',term=3,maximum=2.0,description='',notes='')]
    grades=[dict(activityId=a,studentId=s,value=v) for a,s,v in [('atividade-1','alfa',0.0),('atividade-1','beta-origem',None),('atividade-2','alfa',1.5),('atividade-2','gama',2.0)]]
    drafts=[dict(id='rascunho-ocr',classId='classe-alfa',name='Estudante Fictício Delta',number=None,position=0,page=1,reason='Leitura sintética de OCR pendente',reviewed=False,allowHomonym=False),dict(id='rascunho-incompleto',classId='classe-alfa',name='',number=0,position=1,page=0,reason='Digitação incompleta',reviewed=False,allowHomonym=False)]
    value=dict(classes=[c1,c2],students=students,terms=terms,lessons=lessons,marks=marks,activities=activities,grades=grades,drafts=drafts)
    keys={'terms':lambda r:(r['classId'],r['number']),'marks':lambda r:(r['lessonId'],r['studentId']),'grades':lambda r:(r['activityId'],r['studentId'])}
    return {name:sorted(rows,key=keys.get(name,lambda r:r['id'])) for name,rows in value.items()}

def create_database(path, snapshot):
    schema=json.loads((ROOT/'app/schemas/br.com.diariodeclasse.data.DiaryDatabase/1.json').read_text())['database']
    if path.exists(): raise SystemExit('Destino já existe; não será sobrescrito.')
    path.parent.mkdir(parents=True,exist_ok=True)
    db=sqlite3.connect(path)
    db.execute('PRAGMA foreign_keys=ON')
    for entity in schema['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
        for index in entity['indices']: db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
    for query in schema['setupQueries']: db.execute(query)
    for name in ['classes','students','terms','lessons','activities','marks','grades','drafts']:
        table='import_drafts' if name=='drafts' else name
        for row in snapshot[name]:
            columns=list(row);sql='INSERT INTO "'+table+'" ('+','.join('"'+c+'"' for c in columns)+') VALUES ('+','.join('?' for _ in columns)+')'
            db.execute(sql,[row[c] for c in columns])
    db.execute('PRAGMA user_version=1');db.commit()
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    assert not db.execute('PRAGMA foreign_key_check').fetchall()
    db.close()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--database',type=Path);args=parser.parse_args()
    value=fixture();(ROOT/'test-fixtures/baseline-v1.json').write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
    if args.database: create_database(args.database,value)
    print('Fixture sintética gerada; nenhuma base de usuário foi acessada.')
