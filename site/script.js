(function () {
  const CONFIG_PATH = "./site-config.json";
  const STORAGE_KEY = "kids-phone-guard-feedback-drafts";

  /**
   * Load portal config from a local JSON file.
   * Returns a normalized config object that keeps the page usable even when loading fails.
   */
  async function loadConfig() {
    try {
      const response = await fetch(CONFIG_PATH, { cache: "no-store" });
      if (!response.ok) {
        throw new Error("config fetch failed");
      }

      const config = await response.json();
      return normalizeConfig(config);
    } catch (error) {
      console.warn("Failed to load portal config.", error);
      return normalizeConfig({});
    }
  }

  /**
   * Normalize raw config into the fields used by the page.
   * Fills safe defaults so the site can still be previewed before real data is ready.
   */
  function normalizeConfig(config) {
    return {
      productTitle: config.productTitle || "拉钩守护",
      productSubtitle: config.productSubtitle || "儿童防沉迷家长管理",
      phaseLabel: config.phaseLabel || "亲友测试准备中",
      downloadTitle: config.downloadTitle || "拉钩守护 Android 测试包",
      downloadVersion: config.downloadVersion || "未配置",
      downloadDescription:
        config.downloadDescription ||
        "请将测试 APK 下载链接写入 site-config.json 后再发给测试用户。",
      downloadUrl: config.downloadUrl || "",
      contactName: config.contactName || "待填写",
      contactMethod: config.contactMethod || "待填写",
      contactNote: config.contactNote || "建议补充微信、邮箱或说明文档入口",
      feedbackEndpoint: config.feedbackEndpoint || "",
      supportMessage:
        config.supportMessage ||
        "若反馈无法提交，请导出本地 JSON 后通过微信、邮箱或其他约定渠道发送。",
    };
  }

  /**
   * Fill visible page content using the loaded config.
   */
  function applyConfig(config) {
    document.title = config.productTitle + " - " + config.productSubtitle;
    setText("brandTitle", config.productTitle);
    setText("brandSubtitle", config.productSubtitle);
    setText("heroTitle", config.productTitle);
    setText("heroSubtitle", config.productSubtitle);
    setText("phaseLabel", config.phaseLabel);
    setText("downloadTitle", config.downloadTitle);
    setText("downloadVersion", config.downloadVersion);
    setText("downloadDescription", config.downloadDescription);
    setText("contactName", config.contactName);
    setText("contactMethod", config.contactMethod);
    setText("contactNote", config.contactNote);
    setText("footerProductTitle", config.productTitle);
    setText("footerProductSubtitle", config.productSubtitle);
    setText("footerVersion", "测试版本：" + config.downloadVersion);
    setText("footerUpdatedAt", "配置更新：" + config.updatedAt);

    const feedbackNotice = config.feedbackEndpoint
      ? "当前模式：站内表单会直接提交到已配置接口。"
      : "当前模式：未配置线上接口，表单会先保存在浏览器本地，可手动导出 JSON。";
    setText("feedbackModeNotice", feedbackNotice);

    const downloadButton = document.getElementById("downloadButton");
    if (!downloadButton) {
      return;
    }

    if (config.downloadUrl) {
      downloadButton.href = config.downloadUrl;
      downloadButton.textContent = "下载测试 APK";
      downloadButton.removeAttribute("aria-disabled");
      downloadButton.classList.remove("is-disabled");
    } else {
      downloadButton.href = "#";
      downloadButton.textContent = "暂无可下载安装包";
      downloadButton.setAttribute("aria-disabled", "true");
      downloadButton.classList.add("is-disabled");
    }
  }

  /**
   * Initialize feedback form actions and export behavior.
   */
  function setupFeedbackForm(config) {
    const form = document.getElementById("feedbackForm");
    const exportButton = document.getElementById("exportFeedbackButton");
    const status = document.getElementById("formStatus");
    const screenshotInput = document.getElementById("screenshotInput");
    const selectScreenshotBtn = document.getElementById("selectScreenshotBtn");
    const screenshotPreview = document.getElementById("screenshotPreview");

    if (!form || !exportButton || !status) {
      return;
    }

    // 截图选择
    let selectedFiles = [];

    if (selectScreenshotBtn && screenshotInput) {
      selectScreenshotBtn.addEventListener("click", function () {
        screenshotInput.click();
      });

      screenshotInput.addEventListener("change", function () {
        selectedFiles = Array.from(screenshotInput.files).slice(0, 3);
        renderScreenshotPreviews(selectedFiles, screenshotPreview);
      });
    }

    form.addEventListener("submit", async function (event) {
      event.preventDefault();

      const formData = new FormData(form);

      // 添加截图文件到 FormData
      selectedFiles.forEach(function (file) {
        formData.append("screenshots", file);
      });

      try {
        if (config.feedbackEndpoint) {
          await submitFeedbackWithScreenshots(config.feedbackEndpoint, formData);
          setStatus(status, "反馈和截图已提交成功，感谢你的帮助！", "success");
        } else {
          const payload = buildFeedbackPayload(formData, config);
          saveFeedbackDraft(payload);
          setStatus(status, "已保存到本地浏览器。你也可以点击「导出本地反馈」发给我。", "success");
        }

        form.reset();
        selectedFiles = [];
        if (screenshotPreview) {
          screenshotPreview.innerHTML = "";
        }
      } catch (error) {
        console.error("Feedback submit failed.", error);
        const payload = buildFeedbackPayload(formData, config);
        saveFeedbackDraft(payload);
        setStatus(status, config.supportMessage, "error");
      }
    });

    exportButton.addEventListener("click", function () {
      const drafts = loadFeedbackDrafts();
      if (drafts.length === 0) {
        setStatus(status, "当前没有可导出的本地反馈。", "error");
        return;
      }

      downloadJsonFile("kidsphoneguard-feedback.json", drafts);
      setStatus(status, "已导出本地反馈 JSON。", "success");
    });
  }

  /**
   * Build a structured payload from the submitted form data.
   */
  function buildFeedbackPayload(formData, config) {
    return {
      source: "kidsphoneguard-test-portal",
      submittedAt: new Date().toISOString(),
      currentPortalVersion: config.downloadVersion,
      deviceModel: readField(formData, "deviceModel"),
      systemVersion: readField(formData, "systemVersion"),
      appVersion: readField(formData, "appVersion"),
      issueTime: readField(formData, "issueTime"),
      chargingState: readField(formData, "chargingState"),
      issueType: readField(formData, "issueType"),
      recentContext: readField(formData, "recentContext"),
      affectedTarget: readField(formData, "affectedTarget"),
      actions: readField(formData, "actions"),
      extraNotes: readField(formData, "extraNotes"),
    };
  }

  /**
   * Submit feedback with screenshots using multipart/form-data.
   */
  async function submitFeedbackWithScreenshots(endpoint, formData) {
    const response = await fetch(endpoint, {
      method: "POST",
      body: formData,
    });

    if (!response.ok) {
      const errorData = await response.json().catch(function () {
        return { detail: "提交失败" };
      });
      throw new Error(errorData.detail || "feedback post failed");
    }

    return response.json();
  }

  /**
   * Submit a feedback payload to a configured HTTP endpoint (legacy JSON mode).
   */
  async function submitFeedback(endpoint, payload) {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      throw new Error("feedback post failed");
    }
  }

  /**
   * Render screenshot preview thumbnails.
   */
  function renderScreenshotPreviews(files, container) {
    if (!container) {
      return;
    }

    container.innerHTML = "";

    files.forEach(function (file, index) {
      const reader = new FileReader();
      reader.onload = function (e) {
        const previewItem = document.createElement("div");
        previewItem.className = "screenshot-preview-item";

        const img = document.createElement("img");
        img.src = e.target.result;
        img.alt = "截图 " + (index + 1);

        const removeBtn = document.createElement("button");
        removeBtn.type = "button";
        removeBtn.className = "screenshot-remove-btn";
        removeBtn.innerHTML = "&times;";
        removeBtn.title = "删除这张截图";
        removeBtn.addEventListener("click", function () {
          selectedFiles.splice(index, 1);
          renderScreenshotPreviews(selectedFiles, container);
        });

        previewItem.appendChild(img);
        previewItem.appendChild(removeBtn);
        container.appendChild(previewItem);
      };
      reader.readAsDataURL(file);
    });
  }

  /**
   * Store feedback locally so testing can proceed without a backend.
   */
  function saveFeedbackDraft(payload) {
    const drafts = loadFeedbackDrafts();
    drafts.push(payload);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(drafts));
  }

  /**
   * Read all locally stored feedback drafts.
   */
  function loadFeedbackDrafts() {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }

    try {
      const drafts = JSON.parse(raw);
      return Array.isArray(drafts) ? drafts : [];
    } catch (error) {
      console.warn("Failed to parse local feedback drafts.", error);
      return [];
    }
  }

  /**
   * Download plain JSON data as a local file in the browser.
   */
  function downloadJsonFile(fileName, data) {
    const blob = new Blob([JSON.stringify(data, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  /**
   * Read and trim a single field from FormData.
   */
  function readField(formData, key) {
    const value = formData.get(key);
    return typeof value === "string" ? value.trim() : "";
  }

  /**
   * Update text content on an element if it exists.
   */
  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) {
      element.textContent = value;
    }
  }

  /**
   * Render a concise status line for form submission results.
   */
  function setStatus(element, message, type) {
    element.textContent = message;
    element.classList.remove("is-success", "is-error");

    if (type === "success") {
      element.classList.add("is-success");
    }

    if (type === "error") {
      element.classList.add("is-error");
    }
  }

  /**
   * Boot the portal once the DOM is ready.
   */
  async function initializePortal() {
    const config = await loadConfig();
    applyConfig(config);
    setupFeedbackForm(config);
  }

  initializePortal();
})();
