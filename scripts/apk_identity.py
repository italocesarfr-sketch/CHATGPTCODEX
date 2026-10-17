#!/usr/bin/env python3
"""Lê o certificado público do esquema APK v2. Não lê/gera chave privada.
A verificação criptográfica do APK é feita separadamente pelo apksigner no CI.
"""
import hashlib,json,struct,sys
from pathlib import Path

def identity(path):
    raw=Path(path).read_bytes()
    eocd=raw.rfind(b'PK\x05\x06')
    if eocd<0: raise ValueError('APK inválido')
    central=struct.unpack_from('<I',raw,eocd+16)[0]
    if raw[central-16:central]!=b'APK Sig Block 42': raise ValueError('APK sem bloco v2')
    size=struct.unpack_from('<Q',raw,central-24)[0];pos=central-size
    def lp(data,off=0):
        n=struct.unpack_from('<I',data,off)[0];return data[off+4:off+4+n],off+4+n
    certificates=[]
    while pos<central-24:
        size=struct.unpack_from('<Q',raw,pos)[0];kind=struct.unpack_from('<I',raw,pos+8)[0]
        value=raw[pos+12:pos+8+size];pos+=8+size
        if kind==0x7109871a:
            signers,_=lp(value);offset=0
            while offset<len(signers):
                signer,offset=lp(signers,offset);signed,_=lp(signer);_,off=lp(signed);certs,_=lp(signed,off);cert,_=lp(certs)
                certificates.append(hashlib.sha256(cert).hexdigest())
    if len(certificates)!=1: raise ValueError('Este verificador exige APK v2 com um assinante')
    return {'apkSha256':hashlib.sha256(raw).hexdigest(),'certificateSha256':certificates[0]}

if __name__=='__main__': print(json.dumps(identity(sys.argv[1]),indent=2))
