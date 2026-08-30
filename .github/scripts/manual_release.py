#!/usr/bin/env python3
from __future__ import annotations

import argparse, base64, hashlib, json, os, re, shutil, subprocess, sys, time, zipfile
from pathlib import Path

PKG = "dev.ip.netveil"
BT = "36.0.0"
SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:[.-][0-9A-Za-z][0-9A-Za-z.-]*)?$")
ALLOWED_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.RECEIVE_BOOT_COMPLETED",
}

class ReleaseError(RuntimeError): pass

def stop(msg): raise ReleaseError(msg)
def info(msg): print(msg, flush=True)
def warn(msg): print(f"WARNING: {msg}", file=sys.stderr, flush=True)

def run(args, *, check=True, capture=False):
    cp = subprocess.run(args, text=True, stdout=subprocess.PIPE if capture else None,
                        stderr=subprocess.PIPE if capture else None, check=False)
    if check and cp.returncode:
        detail = (cp.stderr or cp.stdout or "").strip()
        stop(f"command failed ({cp.returncode}): {' '.join(args)}" + (f": {detail}" if detail else ""))
    return cp

def retry(args, attempts=3, *, capture=False):
    last = None
    for n in range(1, attempts + 1):
        last = run(args, check=False, capture=capture)
        if last.returncode == 0: return last
        if n < attempts:
            warn(f"attempt {n}/{attempts} failed: {' '.join(args)}; retrying")
            time.sleep(2)
    detail = (last.stderr or last.stdout or "").strip()
    stop(f"command failed after {attempts} attempts: {' '.join(args)}" + (f": {detail}" if detail else ""))

def env(name):
    value = os.environ.get(name, "")
    if not value: stop(f"required environment value {name} is not configured")
    return value

def boolenv(name, default):
    raw = os.environ.get(name, str(default).lower()).strip().lower()
    if raw not in {"true", "false"}: stop(f"{name} must be true or false")
    return raw == "true"

def gout(*args): return (run(["git", *args], capture=True).stdout or "").strip()
def sha256(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""): h.update(chunk)
    return h.hexdigest()

def tag_sha(tag):
    cp = retry(["git", "ls-remote", "--refs", "origin", f"refs/tags/{tag}"], capture=True)
    line = (cp.stdout or "").strip().splitlines()
    return line[0].split()[0] if line else ""

def release(repo, tag, fields):
    cmd = ["gh", "release", "view", tag, "--repo", repo, "--json", fields]
    for n in range(1, 4):
        cp = run(cmd, check=False, capture=True)
        if cp.returncode == 0:
            try: return json.loads(cp.stdout or "{}")
            except json.JSONDecodeError as e: stop(f"invalid release JSON for {tag}: {e}")
        text = f"{cp.stderr or ''}\n{cp.stdout or ''}".lower()
        if "not found" in text or "http 404" in text: return None
        if n < 3: time.sleep(2)
    stop(f"unable to inspect GitHub release {tag}: {(cp.stderr or cp.stdout or '').strip()}")

def next_patch(v):
    a, b, c = [int(x) for x in v.split("-", 1)[0].split("+", 1)[0].split(".")]
    return f"{a}.{b}.{c+1}"

def choose_version(src, explicit, has_release, draft_release, subject, has_tag):
    explicit = explicit.strip().removeprefix("v")
    if explicit:
        if not SEMVER.fullmatch(explicit): stop(f"requested version {explicit!r} is not a supported semantic version")
        return explicit, "workflow-dispatch"
    if has_release: return (src, "resume-draft") if draft_release else (next_patch(src), "auto-patch")
    if subject == f"chore(release): prepare v{src}": return src, "resume-prepared-source"
    if has_tag: return src, "resume-orphan-tag"
    return next_patch(src), "auto-patch"

def metadata(path):
    text = path.read_text(encoding="utf-8")
    n = re.search(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', text, re.M)
    c = re.search(r'^\s*versionCode\s*=\s*([0-9]+)\s*$', text, re.M)
    if not n or not c: stop("unable to resolve versionName/versionCode from app/build.gradle.kts")
    if not SEMVER.fullmatch(n.group(1)): stop(f"source version {n.group(1)!r} is not supported")
    return n.group(1), int(c.group(1))

def set_metadata(path, version, code):
    text = path.read_text(encoding="utf-8")
    text, a = re.subn(r'(?m)^(\s*versionCode\s*=\s*)\d+(\s*)$', rf'\g<1>{code}\g<2>', text, count=1)
    text, b = re.subn(r'(?m)^(\s*versionName\s*=\s*)"[^"]*"(\s*)$', rf'\g<1>"{version}"\g<2>', text, count=1)
    if a != 1 or b != 1: stop("version metadata assignments are ambiguous")
    path.write_text(text, encoding="utf-8", newline="\n")

def source_branch():
    branch = os.environ.get("GITHUB_REF_NAME", "")
    if os.environ.get("GITHUB_REF_TYPE") != "branch" or not branch:
        stop("Manual Release must be dispatched from a branch because it may commit release metadata")
    retry(["git", "fetch", "--tags", "origin", f"+refs/heads/{branch}:refs/remotes/origin/{branch}"])
    local, remote = gout("rev-parse", "HEAD"), gout("rev-parse", f"origin/{branch}")
    if local != remote: stop(f"selected branch moved before release start: local={local} remote={remote}")
    return branch, local

def sdk_tools():
    root = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or "")
    sm = root / "cmdline-tools/latest/bin/sdkmanager"
    if not sm.is_file():
        found = shutil.which("sdkmanager")
        if not found: stop("sdkmanager is unavailable")
        sm = Path(found)
        if not root:
            root = sm.parents[3]
    retry([str(sm), "platforms;android-36", f"build-tools;{BT}"])
    signer, aapt = root / "build-tools" / BT / "apksigner", root / "build-tools" / BT / "aapt"
    if not signer.is_file() or not aapt.is_file(): stop(f"Android build-tools {BT} verifiers are missing")
    return signer, aapt

def signing(root):
    raw = env("KEYSTORE_BASE64")
    try: data = base64.b64decode(raw, validate=True)
    except Exception as e: stop(f"KEYSTORE_BASE64 is invalid: {e}")
    if not data: stop("decoded release keystore is empty")
    store, alias, key = env("KEYSTORE_PASSWORD"), env("KEY_ALIAS"), env("KEY_PASSWORD")
    ks, props = root / "ReleaseKey.jks", root / "keystore.properties"
    ks.write_bytes(data); ks.chmod(0o600)
    if run(["keytool", "-list", "-keystore", str(ks), "-storepass", store, "-alias", alias], check=False, capture=True).returncode:
        stop("keystore password/alias validation failed")
    props.write_text(f"storeFile={ks}\nstorePassword={store}\nkeyAlias={alias}\nkeyPassword={key}\n", encoding="utf-8")
    props.chmod(0o600)
    return ks, props

def qualify(root, tag, version, code, signer, aapt):
    if run(["gradle", "--no-daemon", "--stacktrace", "--warning-mode=all", ":app:testDebugUnitTest", ":app:lintRelease", ":app:assembleRelease"], check=False).returncode:
        stop("release tests/lint/build failed; no release was published")
    apk = root / "app/build/outputs/apk/release/app-release.apk"
    if not apk.is_file() or not apk.stat().st_size: stop("signed release APK was not produced")
    run([str(signer), "verify", "--verbose", "--print-certs", str(apk)])
    badging = run([str(aapt), "dump", "badging", str(apk)], capture=True).stdout or ""
    p = re.search(r"^package: name='([^']+)'", badging, re.M)
    vc = re.search(r"^package: .* versionCode='([^']+)'", badging, re.M)
    vn = re.search(r"^package: .* versionName='([^']+)'", badging, re.M)
    if not p or p.group(1) != PKG: stop(f"APK package is {p.group(1) if p else 'unknown'}; expected {PKG}")
    if not vn or vn.group(1) != version: stop("APK versionName does not match requested release")
    if not vc or vc.group(1) != str(code): stop("APK versionCode does not match requested release")
    with zipfile.ZipFile(apk) as z:
        for name in ("META-INF/xposed/java_init.list", "META-INF/xposed/module.prop", "assets/country-ip-pack.json"):
            if name not in z.namelist(): stop(f"APK is missing {name}")
    perms = run([str(aapt), "dump", "permissions", str(apk)], capture=True).stdout or ""
    actual_permissions = set(re.findall(r"uses-permission: name='([^']+)'", perms))
    if actual_permissions != ALLOWED_PERMISSIONS:
        stop(f"release APK permissions differ from allow-list: expected={sorted(ALLOWED_PERMISSIONS)} actual={sorted(actual_permissions)}")
    dist = root / "dist"; dist.mkdir(exist_ok=True)
    out = dist / f"{PKG}-{tag}.apk"; shutil.copy2(apk, out)
    ah = sha256(out); sums = dist / "SHA256SUMS.txt"; sums.write_text(f"{ah}  {out.name}\n", encoding="utf-8")
    return out, sums, ah, sha256(sums)

def push_metadata(branch, start_sha, tag, changed):
    if not changed: return gout("rev-parse", "HEAD")
    if run(["git", "diff", "--quiet", "--", "app/build.gradle.kts"], check=False).returncode == 0:
        stop("release metadata was expected to change but did not")
    run(["git", "config", "user.name", "github-actions[bot]"])
    run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"])
    run(["git", "add", "--", "app/build.gradle.kts"]); run(["git", "commit", "-m", f"chore(release): prepare {tag}"])
    release_sha = gout("rev-parse", "HEAD")
    cp = run(["git", "push", f"--force-with-lease=refs/heads/{branch}:{start_sha}", "origin", f"HEAD:{branch}"], check=False, capture=True)
    if cp.returncode: stop("selected branch changed or release metadata push failed; release not published")
    return release_sha

def stage(repo, tag, sha, prerelease):
    cur = release(repo, tag, "isDraft,isPrerelease,targetCommitish")
    tsha = tag_sha(tag)
    if cur and not cur.get("isDraft"):
        if tsha != sha: stop(f"published {tag} belongs to {tsha or 'missing'}, not {sha}; choose another version")
        info(f"Reusing published release {tag}"); return
    if cur:
        retry(["gh", "release", "edit", tag, "--repo", repo, "--target", sha, "--title", f"NetVeil {tag}", "--draft", f"--prerelease={str(prerelease).lower()}"])
        info(f"Resuming draft {tag}")
    else:
        if tsha and tsha != sha: stop(f"tag {tag} points to {tsha}, not {sha}; choose another version")
        cmd = ["gh", "release", "create", tag, "--repo", repo, "--target", sha, "--title", f"NetVeil {tag}", "--generate-notes", "--draft"]
        if prerelease: cmd.append("--prerelease")
        retry(cmd); info(f"Created staging draft {tag}")
    # Draft releases are intentionally allowed to have no refs/tags entry.
    for n in range(5):
        if release(repo, tag, "isDraft,targetCommitish") is not None: return
        if n < 4: time.sleep(2)
    stop(f"draft release {tag} could not be read back")

def assets_match(r, apk_name, ah, sh):
    if not r: return False
    a = [x for x in (r.get("assets") or []) if x.get("name") == apk_name]
    s = [x for x in (r.get("assets") or []) if x.get("name") == "SHA256SUMS.txt"]
    return len(a) == len(s) == 1 and a[0].get("digest") == f"sha256:{ah}" and s[0].get("digest") == f"sha256:{sh}"

def verify_download(repo, tag, apk_name, ah, sh, root):
    d = root / "remote-verify"; shutil.rmtree(d, ignore_errors=True); d.mkdir()
    cp = run(["gh", "release", "download", tag, "--repo", repo, "--pattern", apk_name, "--pattern", "SHA256SUMS.txt", "--dir", str(d), "--clobber"], check=False, capture=True)
    return cp.returncode == 0 and (d/apk_name).is_file() and (d/"SHA256SUMS.txt").is_file() and sha256(d/apk_name) == ah and sha256(d/"SHA256SUMS.txt") == sh

def upload(repo, tag, apk, sums, ah, sh, root):
    if assets_match(release(repo, tag, "assets"), apk.name, ah, sh): info("Qualified assets already present"); return
    ok = False
    for n in range(3):
        cp = run(["gh", "release", "upload", tag, str(apk), str(sums), "--repo", repo, "--clobber"], check=False, capture=True)
        if cp.returncode == 0 or assets_match(release(repo, tag, "assets"), apk.name, ah, sh): ok = True; break
        if n < 2: time.sleep(2)
    if not ok: stop("required release assets could not be uploaded")
    for n in range(8):
        if assets_match(release(repo, tag, "assets"), apk.name, ah, sh): info("Assets verified by GitHub digests"); return
        if n < 7: time.sleep(2)
    if verify_download(repo, tag, apk.name, ah, sh, root): info("Assets verified by downloaded bytes"); return
    stop("remote release assets do not match qualified local bytes")

def final_state(repo, tag, sha, draft, prerelease):
    cur = release(repo, tag, "isDraft,isPrerelease,targetCommitish")
    if not cur: stop(f"release {tag} disappeared")
    if bool(cur.get("isDraft")) != draft or bool(cur.get("isPrerelease")) != prerelease:
        cp = run(["gh", "release", "edit", tag, "--repo", repo, "--target", sha, "--title", f"NetVeil {tag}", f"--draft={str(draft).lower()}", f"--prerelease={str(prerelease).lower()}"], check=False, capture=True)
        if cp.returncode: stop(f"GitHub rejected requested draft={draft} prerelease={prerelease}: {(cp.stderr or cp.stdout or '').strip()}")
    cur = release(repo, tag, "isDraft,isPrerelease,targetCommitish,assets")
    if not cur or bool(cur.get("isDraft")) != draft or bool(cur.get("isPrerelease")) != prerelease: stop("final release state differs from workflow inputs")
    return cur

def summary(result, branch, tag, version, code, source, sha, draft, prerelease):
    p = os.environ.get("GITHUB_STEP_SUMMARY")
    if not p: return
    with Path(p).open("a", encoding="utf-8") as f:
        f.write("## NetVeil Manual Release\n\n")
        for k,v in (("Result",result),("Source branch",branch),("Tag",tag),("versionName",version),("versionCode",code),("Version source",source),("Release source",sha or "not-pushed"),("Draft requested",str(draft).lower()),("Prerelease requested",str(prerelease).lower())): f.write(f"- {k}: `{v}`\n")

def selftest():
    cases = [
        (("0.2.1","1.0.0",False,False,"x",False),("1.0.0","workflow-dispatch")),
        (("0.2.2","",True,True,"x",False),("0.2.2","resume-draft")),
        (("0.2.2","",True,False,"x",True),("0.2.3","auto-patch")),
        (("0.2.2","",False,False,"chore(release): prepare v0.2.2",False),("0.2.2","resume-prepared-source")),
        (("0.2.2","",False,False,"x",True),("0.2.2","resume-orphan-tag")),
        (("0.2.2","",False,False,"x",False),("0.2.3","auto-patch")),
    ]
    for args,want in cases:
        got = choose_version(*args)
        if got != want: raise AssertionError((args, got, want))
    if ALLOWED_PERMISSIONS != {"android.permission.INTERNET", "android.permission.RECEIVE_BOOT_COMPLETED"}:
        raise AssertionError("release permission allow-list changed unexpectedly")
    info(f"manual release planner self-test: {len(cases)} cases passed")

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--self-test", action="store_true"); args = ap.parse_args()
    if args.self_test: selftest(); return 0
    root = Path.cwd(); branch=tag=version=source="unresolved"; code=0; release_sha=""; draft=boolenv("INPUT_DRAFT",False); prerelease=boolenv("INPUT_PRERELEASE",True)
    ks, props = root/"ReleaseKey.jks", root/"keystore.properties"
    try:
        repo=env("GITHUB_REPOSITORY"); env("GH_TOKEN"); branch,start=source_branch()
        src,src_code=metadata(root/"app/build.gradle.kts"); subject=gout("log","-1","--format=%s"); src_tag=f"v{src}"; src_release=release(repo,src_tag,"isDraft")
        version,source=choose_version(src,os.environ.get("INPUT_VERSION_TAG",""),src_release is not None,bool(src_release and src_release.get("isDraft")),subject,bool(tag_sha(src_tag)))
        tag=f"v{version}"; code=src_code+(version!=src); changed=version!=src
        existing=tag_sha(tag)
        if existing and (changed or existing!=start): stop(f"tag {tag} already belongs to {existing}; choose another version")
        if changed: set_metadata(root/"app/build.gradle.kts",version,code)
        signer,aapt=sdk_tools(); signing(root); apk,sums,ah,sh=qualify(root,tag,version,code,signer,aapt)
        release_sha=push_metadata(branch,start,tag,changed); stage(repo,tag,release_sha,prerelease); upload(repo,tag,apk,sums,ah,sh,root); cur=final_state(repo,tag,release_sha,draft,prerelease)
        names=[x.get("name") for x in (cur.get("assets") or [])]
        if names.count(apk.name)!=1 or names.count("SHA256SUMS.txt")!=1: stop("final release is missing authoritative assets")
        tsha=tag_sha(tag)
        if not draft and tsha!=release_sha: stop(f"published tag {tag} is {tsha or 'missing'}, expected {release_sha}")
        if draft and tsha and tsha!=release_sha: stop(f"draft has conflicting tag {tag} -> {tsha}")
        summary("success",branch,tag,version,code,source,release_sha,draft,prerelease); info(f"Release {tag} completed successfully")
        return 0
    except ReleaseError as e:
        print(f"STOP: {e}",file=sys.stderr); summary("failure",branch,tag,version,code,source,release_sha,draft,prerelease); return 1
    finally:
        for p in (ks,props):
            try: p.unlink(missing_ok=True)
            except OSError as e: warn(f"could not remove {p.name}: {e}")

if __name__ == "__main__": raise SystemExit(main())
