"""One-shot 30-minute Sony v60 FLAC playback telemetry; no secret/track metadata capture.

The tester must start the intended album playback beforehand. This script
only polls ADB and stops playback on completion or high device temperature.
It does not modify private Android data or backend.
"""
from __future__ import annotations
import argparse,csv,json,re,subprocess,time
from datetime import datetime,timezone
from pathlib import Path

SERIAL="QV725WEQ54"
PACKAGE="com.bsdung.musicassistantcar"
def adb(*args: str,timeout: int=18)->str:
    try:
        p=subprocess.run(["adb","-s",SERIAL,*args],text=True,capture_output=True,
                         timeout=timeout,encoding="utf-8",errors="replace")
        return p.stdout if p.returncode==0 else ""
    except (subprocess.TimeoutExpired,OSError):
        return ""

def media()->tuple:
    s=adb("shell","dumpsys","media_session")
    at=s.find("package="+PACKAGE)
    x=s[at:at+750] if at>=0 else ""
    m=re.search(r"state=PlaybackState \{state=(\d+), position=(-?\d+), buffered position=(-?\d+), speed=([\d.]+).*?active item id=(-?\d+), error=([^}]+)",x)
    if not m:return ("","", "", "","missing_session")
    return (int(m[1]),int(m[2]),int(m[3]),int(m[5]),m[6].strip()[:70])

def battery():
    s=adb("shell","dumpsys","battery")
    level=re.search(r"\blevel:\s*(\d+)",s)
    temp=re.search(r"\btemperature:\s*(\d+)",s)
    return (int(level[1]) if level else -1,int(temp[1])/10 if temp else -1)

def memory():
    s=adb("shell","dumpsys","meminfo",PACKAGE)
    x=re.search(r"TOTAL PSS:\s*([\d,]+)",s)
    if not x:
        x=re.search(r"^\s*TOTAL\s+([\d,]+)\s",s,re.M)
    return int(x[1].replace(",","")) if x else -1

def vpn():
    s=adb("shell","dumpsys","connectivity",timeout=22)
    return int("ni{VPN CONNECTED" in s and "100.82.19.36/32" in s)

def main(folder:Path,minutes:int):
    if minutes<30 or minutes>45:
        raise ValueError("bounded final RC soak must be 30..45 minutes")
    folder.mkdir(parents=True,exist_ok=True)
    csvpath=folder/"sony_v60_30min_metrics.csv"
    report=folder/"sony_v60_30min_summary.json"
    data=[]
    start=time.monotonic()
    start_utc=datetime.now(timezone.utc).isoformat()
    aborted=False
    try:
        with csvpath.open("w",newline="",encoding="utf-8") as f:
            writer=csv.writer(f)
            writer.writerow(["elapsed_s","utc","media_state","position_ms","buffered_ms","queue_item","media_error","pss_kb","battery_pct","battery_c","vpn_route","app_pid_alive"])
            for idx in range(minutes+1):
                wait=start+idx*60-time.monotonic()
                if wait>0:time.sleep(wait)
                state,pos,buff,item,err=media()
                pss=memory()
                pct,temp=battery()
                tailnet=vpn()
                pid=adb("shell","pidof",PACKAGE).strip()
                row=[round(time.monotonic()-start,1),datetime.now(timezone.utc).isoformat(),
                     state,pos,buff,item,err,pss,pct,temp,tailnet,int(bool(pid))]
                writer.writerow(row);f.flush()
                data.append(row)
                print("SAMPLE {}/{} elapsed={}s state={} pssKB={} batteryC={} vpn={} pid_alive={}".format(idx,minutes,row[0],state,pss,temp,tailnet,int(bool(pid))),flush=True)
                if temp>43:
                    aborted=True
                    print("ABORT safety: battery temperature above 43C",flush=True)
                    break
    finally:
        adb("shell","input","keyevent","KEYCODE_MEDIA_PAUSE")
        playing=sum(1 for row in data if row[2]==3)
        samples=len(data)
        state7=sum(1 for row in data if row[2]==7)
        uniq=len(set(row[5] for row in data if isinstance(row[5],int)))
        result={
            "status":"PASS_TELEMETRY_30MIN" if not aborted and samples==minutes+1 and state7==0 and playing>=int(samples*0.85) and all(row[11] for row in data) and all(row[10] for row in data) else "REVIEW_OR_FAILED",
            "start_utc":start_utc,"end_utc":datetime.now(timezone.utc).isoformat(),
            "planned_minutes":minutes,"samples":samples,"elapsed_seconds":round(time.monotonic()-start,1),
            "playing_samples":playing,"media_error_samples":state7,
            "unique_queue_positions":uniq,
            "vpn_route_samples_good":sum(1 for row in data if row[10]==1),
            "max_battery_temperature_c":max((row[9] for row in data),default=None),
            "pss_kb_start":data[0][7] if data else None,
            "pss_kb_end":data[-1][7] if data else None,
            "safety_temperature_abort":aborted,
            "playback_paused_at_end":True,
            "acoustic_measurement":False,
            "personal_track_titles_or_credentials_logged":False,
            "disclaimer":"ADB telemetry does not prove no audible gaps; backend streaming logs must be correlated separately."
        }
        report.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
        print("SOAK_DONE="+result["status"],flush=True)
        print("SUMMARY="+str(report),flush=True)
    return 0 if result["status"]=="PASS_TELEMETRY_30MIN" else 2

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--minutes",type=int,default=30)
    opts=p.parse_args()
    raise SystemExit(main(opts.out,opts.minutes))
