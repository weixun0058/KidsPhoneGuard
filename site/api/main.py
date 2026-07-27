import json
import os
import shutil
from datetime import datetime
from pathlib import Path
from typing import Optional, List

from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

app = FastAPI(title="拉钩守护反馈收集服务")

# 允许跨域（前端静态页面调用）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["POST", "GET", "OPTIONS"],
    allow_headers=["*"],
)

# 反馈存储目录
FEEDBACK_DIR = Path(__file__).parent.parent / "feedback-data"
FEEDBACK_DIR.mkdir(exist_ok=True)

# 允许的图片格式和大小限制
ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp"}
MAX_IMAGE_SIZE = 10 * 1024 * 1024  # 10MB


class FeedbackPayload(BaseModel):
    source: Optional[str] = None
    submittedAt: Optional[str] = None
    currentPortalVersion: Optional[str] = None
    deviceModel: Optional[str] = None
    systemVersion: Optional[str] = None
    appVersion: Optional[str] = None
    issueTime: Optional[str] = None
    chargingState: Optional[str] = None
    issueType: Optional[str] = None
    recentContext: Optional[str] = None
    affectedTarget: Optional[str] = None
    actions: Optional[str] = None
    extraNotes: Optional[str] = None


def generate_feedback_id() -> str:
    timestamp = datetime.now().strftime("%Y-%m-%d-%H-%M-%S")
    random_suffix = os.urandom(3).hex()
    return f"feedback_{timestamp}_{random_suffix}"


def validate_image(file: UploadFile) -> bool:
    if file.content_type not in ALLOWED_IMAGE_TYPES:
        return False
    return True


@app.post("/api/feedback")
async def submit_feedback(
    deviceModel: Optional[str] = Form(None),
    systemVersion: Optional[str] = Form(None),
    appVersion: Optional[str] = Form(None),
    issueTime: Optional[str] = Form(None),
    chargingState: Optional[str] = Form(None),
    issueType: Optional[str] = Form(None),
    recentContext: Optional[str] = Form(None),
    affectedTarget: Optional[str] = Form(None),
    actions: Optional[str] = Form(None),
    extraNotes: Optional[str] = Form(None),
    screenshots: List[UploadFile] = File(default=[])
):
    """接收用户反馈和截图，保存为文件夹结构"""
    feedback_id = generate_feedback_id()
    feedback_folder = FEEDBACK_DIR / feedback_id
    feedback_folder.mkdir(exist_ok=True)

    # 构建反馈数据
    payload = {
        "source": "kidsphoneguard-test-portal",
        "submittedAt": datetime.now().isoformat(),
        "feedbackId": feedback_id,
        "deviceModel": deviceModel,
        "systemVersion": systemVersion,
        "appVersion": appVersion,
        "issueTime": issueTime,
        "chargingState": chargingState,
        "issueType": issueType,
        "recentContext": recentContext,
        "affectedTarget": affectedTarget,
        "actions": actions,
        "extraNotes": extraNotes,
        "screenshots": []
    }

    # 保存截图
    saved_screenshots = []
    for i, screenshot in enumerate(screenshots):
        if not screenshot.filename:
            continue
        if not validate_image(screenshot):
            continue

        # 检查文件大小
        content = await screenshot.read()
        if len(content) > MAX_IMAGE_SIZE:
            continue

        # 生成安全的文件名
        ext = Path(screenshot.filename).suffix.lower()
        if ext not in {".jpg", ".jpeg", ".png", ".gif", ".webp"}:
            ext = ".png"

        screenshot_filename = f"screenshot_{i+1}{ext}"
        screenshot_path = feedback_folder / screenshot_filename

        try:
            with open(screenshot_path, "wb") as f:
                f.write(content)
            saved_screenshots.append(screenshot_filename)
        except Exception as e:
            print(f"保存截图失败: {e}")
            continue
        finally:
            await screenshot.close()

    payload["screenshots"] = saved_screenshots

    # 保存JSON数据
    json_path = feedback_folder / "data.json"
    try:
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"保存反馈数据失败: {str(e)}")

    return {
        "success": True,
        "message": "反馈已保存",
        "feedbackId": feedback_id,
        "screenshotsCount": len(saved_screenshots)
    }


@app.get("/api/feedback/list")
async def list_feedback():
    """列出所有已保存的反馈文件夹"""
    folders = []
    for item in sorted(FEEDBACK_DIR.iterdir(), reverse=True):
        if item.is_dir():
            json_file = item / "data.json"
            if json_file.exists():
                try:
                    with open(json_file, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    folders.append({
                        "feedbackId": item.name,
                        "submittedAt": data.get("submittedAt", ""),
                        "deviceModel": data.get("deviceModel", ""),
                        "issueType": data.get("issueType", ""),
                        "screenshotsCount": len(data.get("screenshots", []))
                    })
                except Exception:
                    folders.append({
                        "feedbackId": item.name,
                        "submittedAt": "",
                        "deviceModel": "",
                        "issueType": "",
                        "screenshotsCount": 0
                    })

    return {
        "count": len(folders),
        "feedbacks": folders
    }


@app.get("/api/feedback/{feedback_id}")
async def get_feedback(feedback_id: str):
    """获取指定反馈的JSON数据"""
    feedback_folder = FEEDBACK_DIR / feedback_id
    json_path = feedback_folder / "data.json"

    if not json_path.exists():
        raise HTTPException(status_code=404, detail="反馈不存在")

    try:
        with open(json_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"读取失败: {str(e)}")


@app.get("/api/feedback/{feedback_id}/screenshot/{filename}")
async def get_screenshot(feedback_id: str, filename: str):
    """获取反馈中的截图文件"""
    feedback_folder = FEEDBACK_DIR / feedback_id
    screenshot_path = feedback_folder / filename

    # 安全检查：防止目录遍历
    try:
        screenshot_path.resolve().relative_to(feedback_folder.resolve())
    except ValueError:
        raise HTTPException(status_code=403, detail="非法文件路径")

    if not screenshot_path.exists():
        raise HTTPException(status_code=404, detail="截图不存在")

    return FileResponse(screenshot_path)


@app.delete("/api/feedback/{feedback_id}")
async def delete_feedback(feedback_id: str):
    """删除指定反馈文件夹"""
    feedback_folder = FEEDBACK_DIR / feedback_id

    if not feedback_folder.exists():
        raise HTTPException(status_code=404, detail="反馈不存在")

    try:
        shutil.rmtree(feedback_folder)
        return {"success": True, "message": "反馈已删除"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"删除失败: {str(e)}")
