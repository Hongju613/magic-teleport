#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Optional dependency-free build (no Maven required).

Recommended way to build this plugin is Maven: `mvn package` (see pom.xml).
This script is kept for machines without a build tool. It compiles the sources
with plain `javac` against the jars you point it at.

Usage:
    PAPER_API_JAR=/path/paper-api.jar \
    VAULT_API_JAR=/path/VaultAPI.jar \
    BUNGEE_CHAT_JAR=/path/bungeecord-chat.jar \
    python3 tools/build_jar.py [--no-install]

Environment variables:
    PAPER_API_JAR    required  Paper API jar (or any jar providing the Bukkit API)
    VAULT_API_JAR    optional  Vault API jar (compile-only)
    BUNGEE_CHAT_JAR  optional  bungeecord-chat jar (only needed if javac complains
                               about BaseComponent in CommandSender#sendMessage)
    JAVAC            optional  path to javac (default: javac on PATH)
    INSTALL_DIR      optional  plugins folder to copy the jar into (default: no install)
"""
import os
import shutil
import subprocess
import sys
import zipfile

PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_JAVA = os.path.join(PROJ, 'src', 'main', 'java')
SRC_RES = os.path.join(PROJ, 'src', 'main', 'resources')
BUILD = os.path.join(PROJ, 'build')
NAME, VER = 'magic-teleport', '1.0.0'
OUT_JAR = os.path.join(PROJ, '%s-%s.jar' % (NAME, VER))
LOG = os.path.join(PROJ, 'build_log.txt')


def env(name):
    v = os.environ.get(name)
    return v if v and os.path.exists(v) else None


def main():
    javac = env('JAVAC') or 'javac'
    required = ['PAPER_API_JAR']
    for k in required:
        if not env(k):
            print('Missing %s (set it to the path of the jar).' % k)
            return 2

    sources = []
    for root, _dirs, files in os.walk(SRC_JAVA):
        for f in files:
            if f.endswith('.java'):
                sources.append(os.path.join(root, f))
    if not sources:
        print('No .java sources found under ' + SRC_JAVA)
        return 2

    cp_parts = [env('PAPER_API_JAR')]
    for k in ('VAULT_API_JAR', 'BUNGEE_CHAT_JAR'):
        if env(k):
            cp_parts.append(env(k))
    cp = os.pathsep.join(cp_parts)

    if os.path.isdir(BUILD):
        shutil.rmtree(BUILD)
    os.makedirs(BUILD)

    cmd = [javac, '-encoding', 'UTF-8', '-nowarn', '-cp', cp, '-d', BUILD] + sources
    with open(LOG, 'w', encoding='utf-8', errors='replace') as log:
        p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        out = p.stdout.decode('utf-8', 'replace')
        log.write(out)
    if p.returncode != 0:
        print('javac failed (exit %d), see %s' % (p.returncode, LOG))
        try:
            sys.stdout.buffer.write(out[-4000:].encode('utf-8', 'replace'))
            sys.stdout.buffer.write(b'\n')
        except Exception:
            pass
        return 1

    for root, _dirs, files in os.walk(SRC_RES):
        for f in files:
            full = os.path.join(root, f)
            rel = os.path.relpath(full, SRC_RES)
            dst = os.path.join(BUILD, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(full, dst)

    if os.path.exists(OUT_JAR):
        os.remove(OUT_JAR)
    with zipfile.ZipFile(OUT_JAR, 'w', zipfile.ZIP_DEFLATED) as z:
        for root, _dirs, files in os.walk(BUILD):
            for f in files:
                full = os.path.join(root, f)
                z.write(full, os.path.relpath(full, BUILD).replace('\\', '/'))

    print('Built %s (%d bytes)' % (OUT_JAR, os.path.getsize(OUT_JAR)))
    install = env('INSTALL_DIR')
    if install:
        dst = os.path.join(install, os.path.basename(OUT_JAR))
        shutil.copyfile(OUT_JAR, dst)
        a, b = os.path.getsize(OUT_JAR), os.path.getsize(dst)
        print('Copied to %s (%d bytes, %s)' % (dst, b, 'ok' if a == b else 'SIZE MISMATCH'))
        return 0 if a == b else 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
