#!/usr/bin/env python3
"""Resgate local SOMENTE LEITURA do app debug. Não instala, desinstala ou limpa o celular.
O app é encerrado antes da cópia para obter SQLite/WAL consistentes. Todos os arquivos
ficam apenas no diretório privado local informado pelo professor.
"""
import argparse,hashlib,json,os,sqlite3,subprocess,sys,re
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parent.parent
PACKAGE='br.com.diariodeclasse'
IDENTITY='99b868cf68cf5dffb82edf267ddc78be'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    if args.output.exists(): raise SystemExit('Destino já existe. Escolha uma pasta nova; nada será sobrescrito.')
    adb=['adb','-s',args.serial]
    def run(parts,check=True): return subprocess.run(adb+parts,check=check,capture_output=True)
    run(['shell','run-as',PACKAGE,'id']) # falha em aplicativo não depurável, sem tentar root
    version=run(['shell','dumpsys','package',PACKAGE]).stdout.decode(errors='replace')
    match=re.search(r'versionCode=(\d+)',version)
    if not match: raise SystemExit('Não foi possível identificar a versão instalada.')
    run(['shell','am','force-stop',PACKAGE])
    args.output.mkdir(parents=True,mode=0o700)
    os.chmod(args.output,0o700)
    for filename in ['diario.db','diario.db-wal','diario.db-shm']:
        raw=run(['exec-out','run-as',PACKAGE,'cat','databases/'+filename],check=False)
        if raw.returncode:
            if filename=='diario.db': raise SystemExit('Banco não foi copiado. O app instalado permanece intacto.')
            continue
        path=args.output/filename;path.write_bytes(raw.stdout);os.chmod(path,0o600)
    # O SQLite abre somente a cópia local; .backup incorpora o WAL em um banco normalizado.
    local=sqlite3.connect(args.output/'diario.db')
    if local.execute('PRAGMA user_version').fetchone()[0]!=1: raise SystemExit('Versão não suportada. Cópia bruta preservada, sem conversão.')
    if local.execute('SELECT identity_hash FROM room_master_table WHERE id=42').fetchone()[0]!=IDENTITY: raise SystemExit('Esquema não suportado; cópia bruta preservada.')
    if local.execute('PRAGMA integrity_check').fetchone()[0]!='ok' or local.execute('PRAGMA foreign_key_check').fetchall(): raise SystemExit('Cópia não íntegra. Não desinstale o aplicativo.')
    normalized=sqlite3.connect(args.output/'diario-normalizado.db');local.backup(normalized);normalized.close()
    schema=json.loads((ROOT/'app/schemas/br.com.diariodeclasse.data.DiaryDatabase/1.json').read_text())['database']
    known={e['tableName']:[f['columnName'] for f in e['fields']] for e in schema['entities']}
    bools={'classes':{'countJustified','startPresent'},'import_drafts':{'reviewed','allowHomonym'}}
    snapshot={}
    orders={'terms':'classId,number','marks':'lessonId,studentId','grades':'activityId,studentId'}
    for field in ['classes','students','terms','lessons','marks','activities','grades','drafts']:
        table='import_drafts' if field=='drafts' else field
        cols=[r[1] for r in local.execute('PRAGMA table_info("'+table+'")')]
        if set(cols)!=set(known[table]): raise SystemExit('Colunas desconhecidas; conversão interrompida sem alterar a origem.')
        records=[]
        for row in local.execute('SELECT * FROM "'+table+'" ORDER BY '+orders.get(table,'id')):
            record=dict(zip(cols,row))
            for column in bools.get(table,set()): record[column]=bool(record[column])
            records.append(record)
        snapshot[field]=records
    local.close()
    payload=json.dumps(snapshot,ensure_ascii=False,separators=(',',':'),allow_nan=False)
    envelope={'format':'diario-de-classe-backup','formatVersion':1,'applicationId':PACKAGE,'databaseVersion':1,'schemaIdentityHash':IDENTITY,'createdAt':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'appVersionCode':int(match[1]),'sourceCertificateSha256':None,'payloadSha256':hashlib.sha256(payload.encode()).hexdigest(),'payload':payload}
    raw=json.dumps(envelope,ensure_ascii=False,separators=(',',':')).encode()
    if len(raw)>32*1024*1024: raise SystemExit('Arquivo excede 32 MB. Cópias locais preservadas para suporte.')
    backup=args.output/'resgate-diario.json';backup.write_bytes(raw);os.chmod(backup,0o600)
    for p in args.output.iterdir():
        if p.is_file(): os.chmod(p,0o600)
    print('Resgate local e integridade SQLite concluídos. Confirme a restauração no app novo com os arquivos locais.')
    print('O app instalado NÃO foi desinstalado ou limpo. Nenhum arquivo foi enviado a servidor.')

if __name__=='__main__':
    try: main()
    except (subprocess.CalledProcessError,sqlite3.Error): raise SystemExit('Não foi possível concluir o resgate. Nenhum dado da origem foi apagado. Confira USB/ADB e se o APK é depurável.')
