# HƯỚNG DẪN CHI TIẾT TRIỂN KHAI DATALENS STUDIO LÊN VERCEL DÀNH CHO MACOS

> **Mục tiêu:** Giúp bạn từ máy macOS đẩy toàn bộ dự án lên **GitHub**, triển khai Backend lên **Render.com** (miễn phí), đưa Frontend lên **Vercel** và có được đường link chính thức `https://<ten-du-an>.vercel.app` để nộp bài cho thầy cô.

---

## 📑 MỤC LỤC
1. [Hiểu đúng kiến trúc triển khai](#1-hiểu-đúng-kiến-trúc-triển-khai)
2. [Chuẩn bị tài khoản cần thiết](#2-chuẩn-bị-tài-khoản-cần-thiết)
3. [Giai đoạn 1: Đẩy mã nguồn lên GitHub từ macOS](#giai-đoạn-1-đẩy-mã-nguồn-lên-github-từ-macos)
4. [Giai đoạn 2: Triển khai Backend Java lên Render.com](#giai-đoạn-2-triển-khai-backend-java-lên-rendercom)
5. [Giai đoạn 3: Cập nhật cầu nối vercel.json & Đẩy lại Git](#giai-đoạn-3-cập-nhật-cầu-nối-verceljson--đẩy-lại-git)
6. [Giai đoạn 4: Triển khai Frontend lên Vercel để lấy link nộp bài](#giai-đoạn-4-triển-khai-frontend-lên-vercel-để-lấy-link-nộp-bài)
7. [Giai đoạn 5: Kiểm tra nghiệm thu bài tập trên Vercel](#giai-đoạn-5-kiểm-tra-nghiệm-thu-bài-tập-trên-vercel)
8. [Xử lý các lỗi thường gặp trên macOS & Cloud](#8-xử-lý-các-lỗi-thường-gặp-trên-macos--cloud)

---

## 1. Hiểu đúng kiến trúc triển khai

Dự án DataLens Studio là ứng dụng **Full-Stack chuyên nghiệp**:
* **Frontend (Giao diện):** HTML5, CSS3, JavaScript ES Modules, Chart.js $\rightarrow$ Chạy trên **Vercel** (Cung cấp đường link chính thức nộp bài cho giảng viên).
* **Backend (Xử lý dữ liệu & AI):** Java 17, Spring Boot 3, Apache POI/PDFBox, H2 Database $\rightarrow$ Chạy trên **Render.com** (Nền tảng Cloud miễn phí cho Java).
* **Cầu nối `vercel.json` (Reverse Proxy):** Khi người chấm bài truy cập link Vercel và tải tệp lên, Vercel tự động chuyển tiếp yêu cầu sang Render mà không gây ra bất kỳ lỗi CORS hay bảo mật nào.

```
       [Giảng viên chấm bài]
                 │
                 ▼ (Truy cập: https://datalens-studio.vercel.app)
  ┌──────────────────────────────┐
  │     VERCEL (Frontend)        │ ── Tải giao diện SPA cực nhanh
  └──────────────┬───────────────┘
                 │ (Tự động Proxy ngầm qua /api/*)
                 ▼
  ┌──────────────────────────────┐
  │    RENDER.COM (Backend Java) │ ── Xử lý Excel/Word/PDF & Gọi Google Gemini AI
  └──────────────────────────────┘
```

---

## 2. Chuẩn bị tài khoản cần thiết

Trước khi bắt đầu, hãy chuẩn bị 3 tài khoản (đều miễn phí 100%):

1. **GitHub:** [https://github.com](https://github.com) *(Bạn đã có)*.
2. **Vercel:** Truy cập [https://vercel.com/signup](https://vercel.com/signup) $\rightarrow$ Chọn **"Continue with GitHub"** (Đăng nhập liên kết với tài khoản GitHub).
3. **Render:** Truy cập [https://render.com](https://render.com) $\rightarrow$ Bấm **"Sign Up"** $\rightarrow$ Chọn **"GitHub"**.

---

## Giai đoạn 1: Đẩy mã nguồn lên GitHub từ macOS

### Bước 1.1: Tạo Repository mới trên GitHub
1. Đăng nhập vào GitHub.
2. Bấm vào dấu **`+`** ở góc trên bên phải $\rightarrow$ Chọn **"New repository"**.
3. Điền thông tin:
   * **Repository name:** ví dụ `datalens-studio` (viết liền, không dấu).
   * **Public / Private:** Chọn **Public** (để Vercel và Render dễ dàng kết nối miễn phí).
   * **Không tích** vào ô *"Add a README file"* (vì dự án của chúng ta đã có sẵn README).
4. Bấm **"Create repository"**.
5. Giữ nguyên trang web GitHub đó để lấy đường link (dạng `https://github.com/<tai-khoan>/datalens-studio.git`).

---

### Bước 1.2: Mở Terminal trên macOS và đẩy code

1. Mở ứng dụng **Terminal** trên máy Mac (Bấm tổ hợp phím `Command + Space`, gõ `Terminal` và nhấn `Enter`).
2. Di chuyển vào thư mục dự án của bạn:
   ```bash
   cd /duong/dan/toi/thu-muc/demo
   ```
   *(Mẹo: Bạn có thể gõ `cd ` rồi kéo thả thư mục dự án từ Finder vào cửa sổ Terminal).*

3. Cấp quyền thực thi cho file `gradlew` (bước bắt buộc trên macOS/Linux):
   ```bash
   chmod +x backend/gradlew
   ```

4. Chạy chuỗi lệnh sau để khởi tạo và đẩy code lên GitHub:
   ```bash
   # 1. Khởi tạo Git cục bộ (nếu chưa có)
   git init

   # 2. Thêm toàn bộ các file vào danh sách chuẩn bị đẩy
   git add .

   # 3. Tạo commit đầu tiên
   git commit -m "feat: complete DataLens Studio full-stack"

   # 4. Đổi nhánh chính thành main
   git branch -M main

   # 5. Liên kết tới kho GitHub bạn vừa tạo (thay bằng link thực tế của bạn)
   git remote add origin https://github.com/<tai-khoan-cua-ban>/datalens-studio.git

   # 6. Đẩy code lên GitHub
   git push -u origin main
   ```

*(Nếu Terminal yêu cầu đăng nhập GitHub, bạn đăng nhập bằng Personal Access Token hoặc qua trình duyệt theo hướng dẫn của GitHub).*

---

## Giai đoạn 2: Triển khai Backend Java lên Render.com

Do Vercel chỉ chạy Frontend, chúng ta đưa Backend Java lên Render hoàn toàn miễn phí.

### Bước 2.1: Tạo Web Service mới
1. Đăng nhập vào [Render Dashboard](https://dashboard.render.com).
2. Bấm nút **"New +"** (góc trên bên phải) $\rightarrow$ Chọn **"Web Service"**.
3. Chọn tùy chọn **"Build and deploy from a Git repository"** $\rightarrow$ Bấm **"Next"**.
4. Tìm và bấm **"Connect"** cạnh kho lưu trữ `datalens-studio` mà bạn vừa đẩy lên GitHub.

---

### Bước 2.2: Cấu hình thông số trên Render
Điền chính xác các mục sau:
* **Name:** ví dụ `datalens-backend-api` (hoặc tên tùy thích).
* **Region:** Chọn **Singapore** (gần Việt Nam nhất, tốc độ phản hồi nhanh nhất).
* **Branch:** `main`.
* **Root Directory:** Điền `backend` *(Rất quan trọng: để Render trỏ đúng vào thư mục chứa Java & Dockerfile)*.
* **Language:** Render sẽ tự động nhận diện **Docker** (vì đã có file `backend/Dockerfile` chuẩn bị sẵn).
* **Instance Type:** Chọn gói **Free** ($0/tháng).

---

### Bước 2.3: Thêm biến môi trường (Environment Variables) - Tùy chọn
Cuộn xuống phần **Environment Variables**, nếu bạn muốn cấu hình sẵn API Key của Gemini trên server:
* **Key:** `GEMINI_API_KEY`
* **Value:** Dán mã khóa Gemini của bạn vào đây (ví dụ: `AQ.Ab8RN6...`).
*(Nếu không điền, người dùng vẫn có thể nhập key trực tiếp trên web).*

---

### Bước 2.4: Bấm Deploy & Lấy link Backend
1. Bấm nút **"Create Web Service"**.
2. Render sẽ tự động tải mã nguồn, build file JAR bằng Java 17 và khởi động máy chủ (quá trình này mất khoảng 2-4 phút).
3. Khi màn hình hiện chữ **`Live`** màu xanh lá và trong log có dòng:
   ```text
   Tomcat started on port 8080 (http) with context path ''
   Started DataLensApplication in ... seconds
   ```
4. Nhìn lên góc trên bên trái, copy đường link của Backend Render (có dạng: `https://datalens-backend-api.onrender.com`).

---

## Giai đoạn 3: Cập nhật cầu nối vercel.json & Đẩy lại Git

Bây giờ bạn đã có đường link Backend từ Render, hãy cập nhật vào tệp cấu hình của Vercel:

### Bước 3.1: Chỉnh sửa file `vercel.json`
Mở file `vercel.json` ở thư mục gốc của dự án, thay thế đoạn `https://YOUR_RENDER_BACKEND_URL` bằng chính đường link Render bạn vừa copy ở Bước 2.4:

```json
{
  "outputDirectory": "frontend",
  "cleanUrls": true,
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "https://datalens-backend-api.onrender.com/api/:path*"
    }
  ]
}
```
*(Đồng thời cập nhật nội dung tương tự vào file `frontend/vercel.json`)*.

---

### Bước 3.2: Đẩy cập nhật lên GitHub
Mở Terminal trên máy Mac và chạy:
```bash
git add .
git commit -m "chore: connect Vercel rewrite to live Render backend"
git push origin main
```

---

## Giai đoạn 4: Triển khai Frontend lên Vercel để lấy link nộp bài

Đây là bước quan trọng nhất để tạo ra đường link chính thức nộp cho thầy cô.

### Bước 4.1: Nhập dự án từ GitHub vào Vercel
1. Đăng nhập vào [Vercel Dashboard](https://vercel.com/dashboard).
2. Bấm nút **"Add New..."** (góc phải) $\rightarrow$ Chọn **"Project"**.
3. Tại danh sách GitHub repositories, tìm `datalens-studio` $\rightarrow$ Bấm nút **"Import"**.

---

### Bước 4.2: Cấu hình thông số trên Vercel
Tại màn hình **Configure Project**:
* **Project Name:** ví dụ `datalens-studio` (đây sẽ là tên link web của bạn: `https://datalens-studio.vercel.app`).
* **Framework Preset:** Chọn **"Other"**.
* **Root Directory:** Bấm nút **Edit** $\rightarrow$ Chọn thư mục **`frontend`** $\rightarrow$ Bấm **Continue**.
* **Build and Output Settings:** Giữ nguyên mặc định (vì frontend là HTML/JS thuần, không cần lệnh build).

---

### Bước 4.3: Bấm Deploy
1. Bấm nút **"Deploy"** màu xanh.
2. Vercel sẽ triển khai giao diện chỉ trong vòng **10 - 20 giây**.
3. Màn hình pháo hoa chúc mừng xuất hiện! 🎉
4. Bấm vào nút **"Continue to Dashboard"** hoặc bấm trực tiếp vào ảnh chụp trang web để mở đường link chính thức.
   👉 Đường link bài tập của bạn có dạng: **`https://datalens-studio.vercel.app`**.

---

## Giai đoạn 5: Kiểm tra nghiệm thu bài tập trên Vercel

Sau khi có link Vercel, hãy thực hiện kiểm tra như một người chấm bài:

1. **Mở link Vercel trên trình duyệt:** Giao diện Enterprise Data Studio tải lên mượt mà, đầy đủ logo và các bước.
2. **Kiểm tra API Key AI:**
   * Bấm vào nút **`🔑 Gemini API Key`** ở góc trên bên phải.
   * Dán khóa API của bạn $\rightarrow$ Bấm **`⚡ Kiểm tra kết nối`**.
   * Hệ thống báo xanh lá: *"Kết nối thành công tới mô hình gemini-3.1-flash-lite!"*.
   * Bấm **`Lưu & Kích hoạt`**.
3. **Thử nghiệm Bước 1 (Upload):** Kéo thả một tệp `.xlsx`, `.csv`, `.docx` hoặc `.pdf` bất kỳ lên $\rightarrow$ Dữ liệu hiển thị đầy đủ trong bảng Data Grid.
4. **Thử nghiệm Bước 2 (Làm sạch):** Bấm *"Áp dụng làm sạch & Kiểm định"* $\rightarrow$ Kiểm tra các ô được highlight và nhật ký kiểm toán.
5. **Thử nghiệm Bước 3 (Phân tích & AI):** Xem biểu đồ phân tích và đọc các nhận định kinh doanh bằng tiếng Việt do Gemini AI sinh ra.
6. **Thử nghiệm Bước 4 (Xuất file):** Tải thử file **Excel (.xlsx)** (có đủ 3 sheet và định dạng số chuẩn) hoặc **CSV (.csv)** (mở trong Excel không bị lỗi font tiếng Việt).

---

## 8. Xử lý các lỗi thường gặp trên macOS & Cloud

### ⚠️ 1. Render gói miễn phí bị "ngủ đông" (Spin down)
* **Hiện tượng:** Nếu sau 15 phút không có ai truy cập, Render sẽ tạm thời đưa server vào trạng thái ngủ để tiết kiệm tài nguyên. Khi giảng viên bấm vào link lần đầu tiên, web có thể phản hồi chậm mất khoảng 30 - 50 giây để "đánh thức" server.
* **Cách khắc phục:** Trước giờ nộp bài hoặc trước khi thầy cô chấm, bạn chỉ cần mở link web của mình lên thao tác thử 1 lượt để server Render thức dậy sẵn sàng.

### ⚠️ 2. Quyền thực thi gradlew trên macOS (`Permission denied`)
* **Lỗi:** `./gradlew: Permission denied`
* **Xử lý:** Mở Terminal và chạy: `chmod +x backend/gradlew` sau đó commit và push lại lên GitHub.

### ⚠️ 3. Muốn chạy thử nghiệm cục bộ trên macOS trước khi đẩy
Nếu bạn muốn chạy test offline trên máy Mac của mình:
```bash
# Mở Terminal
cd /duong/dan/toi/backend
./gradlew bootRun
```
Sau đó mở trình duyệt tại: `http://localhost:8080`.

---

## 🎓 Mẫu thông tin nộp bài chuẩn chỉnh gửi Giảng viên

Bạn có thể copy đoạn này để nộp bài qua Email, Google Classroom hoặc hệ thống LMS của trường:

```markdown
Kính gửi Thầy/Cô,

Em xin phép gửi thông tin nộp bài tập lớn:
- Tên đề tài: DataLens Studio – Nền tảng Làm sạch & Phân tích Dữ liệu Doanh nghiệp tích hợp Google Gemini AI
- Đường link sản phẩm trực tuyến (Vercel): https://<ten-du-an-cua-ban>.vercel.app
- Kho mã nguồn GitHub: https://github.com/<tai-khoan-cua-ban>/<ten-repository>

Hệ thống hỗ trợ:
1. Tiếp nhận và bóc tách dữ liệu thực tế đa định dạng (.xlsx, .csv, .json, .docx, .pdf với công nghệ AI Vision).
2. Tự động chuẩn hóa dữ liệu, xử lý giá trị thiếu, phát hiện ngoại lai IQR và lưu vết kiểm toán (Audit Trail).
3. Dashboard KPI tương tác và Trí tuệ nhân tạo Gemini 3.1 Flash sinh nhận định kinh doanh & khuyến nghị chiến lược.
4. Xuất báo cáo dữ liệu sạch chuẩn quốc tế: Excel (3 sheets định dạng số chuẩn) và CSV UTF-8 BOM.

Em xin chân thành cảm ơn Thầy/Cô!
```
