package cn.javaex.yaoqishan.service.qiniu_info;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.common.Zone;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import com.qiniu.util.UrlSafeBase64;

import cn.javaex.yaoqishan.constant.ErrorMsg;
import cn.javaex.yaoqishan.dao.qiniu_info.IQiniuInfoDAO;
import cn.javaex.yaoqishan.exception.QingException;
import cn.javaex.yaoqishan.view.QiniuInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

@Service("QiniuInfoService")
public class QiniuInfoService {
	@Autowired
	private IQiniuInfoDAO iQiniuInfofoDAO;

	/**
	 * 鏍规嵁鎸囧畾绫诲瀷鏌ヨ閰嶇疆璁板綍
	 * @param type 绫诲瀷
	 * @return
	 */
	public QiniuInfo selectByType(String type) {
		return iQiniuInfofoDAO.selectByType(type);
	}

	/**
	 * 淇濆瓨閰嶇疆
	 * @param qiniuInfo
	 */
	public void save(QiniuInfo qiniuInfo) {
		iQiniuInfofoDAO.update(qiniuInfo);
	}

	/**
	 * 涓婁紶鏈湴鍥剧墖鍒颁竷鐗涗簯
	 * @param file
	 * @param qiniuInfo
	 * @return
	 * @throws IOException
	 * @throws QingException
	 */
	public String uploadImage(MultipartFile file, QiniuInfo qiniuInfo) throws IOException, QingException {
		/**
		 * 鏋勯�犱竴涓甫鎸囧畾Zone瀵硅薄鐨勯厤缃被
		 * 鍗庝笢 : Zone.zone0()
		 * 鍗庡寳 : Zone.zone1()
		 * 鍗庡崡 : Zone.zone2()
		 * 鍖楃編 : Zone.zoneNa0()
		 */
		Configuration cfg = new Configuration(Zone.zone0());
		// ...鍏朵粬鍙傛暟鍙傝�冪被娉ㄩ噴
		UploadManager uploadManager = new UploadManager(cfg);
		// ...鐢熸垚涓婁紶鍑瘉锛岀劧鍚庡噯澶囦笂浼�
		String accessKey = qiniuInfo.getAk();
		String secretKey = qiniuInfo.getSk();
		String bucket = qiniuInfo.getBucket();
		// 榛樿涓嶆寚瀹歬ey鐨勬儏鍐典笅锛屼互鏂囦欢鍐呭鐨刪ash鍊间綔涓烘枃浠跺悕
		String key = null;
		
		String imgUrl = "";
		try {
			// 鏁版嵁娴佷笂浼�
			InputStream byteInputStream = file.getInputStream();
			Auth auth = Auth.create(accessKey, secretKey);
			String upToken = auth.uploadToken(bucket);
			try {
				Response response = uploadManager.put(byteInputStream, key, upToken, null, null);
				// 瑙ｆ瀽涓婁紶鎴愬姛鐨勭粨鏋�
				DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
//				System.out.println(putRet.key);
//				System.out.println(putRet.hash);
				String deleteKey = putRet.hash;
				imgUrl = qiniuInfo.getDomain() + putRet.hash;

				// 鍒ゆ柇鏄惁闇�瑕佸鍥剧墖杩涜瑁佸壀
				if ("0".equals(qiniuInfo.getWidth()) || "0".equals(qiniuInfo.getHeight())) {
					
				} else {
					// 鍥剧墖瑁佸壀鍚庡啀娆′笂浼�
					imgUrl = uploadCutImage(qiniuInfo, auth, cfg, bucket, imgUrl);
					// 鍒犻櫎鍘熷浘
					deleteFile(auth, cfg, bucket, deleteKey);
				}
			} catch (QiniuException ex) {
				Response r = ex.response;
				System.err.println(r.toString());
				try {
					System.err.println(r.bodyString());
				} catch (QiniuException ex2) {
					// ignore
				}
				throw new QingException(ErrorMsg.ERROR_500002);
			}
		} catch (UnsupportedEncodingException ex) {
			// ignore
			throw new QingException(ErrorMsg.ERROR_500002);
		}
		
		return imgUrl;
	}
	
	/**
	 * 杩滅▼鍥剧墖涓婁紶鍒颁竷鐗涗簯
	 * @param url 杩滅▼鍥剧墖鍦板潃
	 * @param qiniuInfo 涓冪墰浜戝璞�
	 * @return
	 * @throws QingException
	 */
	public String uploadImageByYuancheng(String url, QiniuInfo qiniuInfo) throws QingException {

		Configuration cfg = new Configuration(Region.huanan());
		String accessKey = qiniuInfo.getAk();
		String secretKey = qiniuInfo.getSk();
		String bucket = qiniuInfo.getBucket();
		String key = null;
		
		String imgUrl = "";
		
		Auth auth = Auth.create(accessKey, secretKey);
		BucketManager bucketManager = new BucketManager(auth, cfg);
		try {
			int index = url.indexOf(".jpg");
			if (index>0) {
				url = url.substring(0, index) + ".jpg";
			}
			
			String hash = bucketManager.fetch(url, bucket, key).hash;
//			System.out.println(hash);
			String deleteKey = hash;
			imgUrl = qiniuInfo.getDomain() + hash;
			
			if ("0".equals(qiniuInfo.getWidth()) || "0".equals(qiniuInfo.getHeight())) {
				
			} else {
				imgUrl = uploadCutImage(qiniuInfo, auth, cfg, bucket, imgUrl);
				deleteFile(auth, cfg, bucket, deleteKey);
			}
		} catch (QiniuException e) {
			e.printStackTrace();
			throw new QingException(ErrorMsg.ERROR_500001);
		}
		
		return imgUrl;
	}
	
	/**
	 * 鍥剧墖瑁佸壀鍚庡啀娆′笂浼�
	 * @param qiniuInfo
	 * @param auth
	 * @param cfg
	 * @param bucket
	 * @param imgUrl
	 * @return
	 * @throws QingException 
	 */
	public String uploadCutImage(QiniuInfo qiniuInfo, Auth auth, Configuration cfg, String bucket, String imgUrl) throws QingException {
		String apiCut = "";
		String width = qiniuInfo.getWidth();
		String height = qiniuInfo.getHeight();
		String compress = qiniuInfo.getCompress();
		
		// 鍒ゆ柇鏄惁闇�瑕佽鍓�
		if ("0".equals(width) || "0".equals(height)) {
			// 涓嶈鍓�
		} else {
			// 瑁佸壀
			apiCut = "?imageView2/1/w/"+width+"/h/"+height;
		}
		
		// 鍒ゆ柇鏄惁闇�瑕佸帇缂�
		if (!"".equals(apiCut)) {
			if ("0".equals(compress)) {
				apiCut += "/q/100";
			} else {
				apiCut += "/q/"+compress+"|imageslim";
			}
		}
		
		// 瀹炰緥鍖栦竴涓狟ucketManager瀵硅薄
		BucketManager bucketManager = new BucketManager(auth, cfg);
		// 瑕乫etch鐨剈rl
		String url = imgUrl + apiCut;
//		System.out.println(url);
		
		try {
			// 璋冪敤fetch鏂规硶鎶撳彇鏂囦欢
			String hash = bucketManager.fetch(url, bucket, null).hash;
//			System.out.println(hash);
			
			return qiniuInfo.getDomain() + hash;
		} catch (QiniuException e) {
			e.printStackTrace();
			throw new QingException(ErrorMsg.ERROR_500003);
		}
	}

	/**
	 * 鍒犻櫎涓冪墰浜戠┖闂寸殑鏂囦欢
	 * @param auth
	 * @param cfg
	 * @param bucket 绌洪棿鍚嶇О
	 * @param fileName 鏂囦欢鍚嶇О
	 */
	public void deleteFile(Auth auth, Configuration cfg, String bucket, String fileName) {
		//鏋勯�犱竴涓甫鎸囧畾Zone瀵硅薄鐨勯厤缃被
//		Configuration cfg = new Configuration(Zone.zone0());
		//...鍏朵粬鍙傛暟鍙傝�冪被娉ㄩ噴
		BucketManager bucketManager = new BucketManager(auth, cfg);
		try {
			bucketManager.delete(bucket, fileName);
		} catch (QiniuException ex) {
			// 濡傛灉閬囧埌寮傚父锛岃鏄庡垹闄ゅけ璐�
			System.err.println(ex.code());
			System.err.println(ex.response.toString());
		}
	}

	/**
	 * 涓婁紶base64鍥剧墖
	 * @param file64
	 * @param qiniuInfo
	 * @return
	 * @throws IOException 
	 */
	public String uploadAvatar(String file64, QiniuInfo qiniuInfo) throws IOException {
		// 瀵嗛挜閰嶇疆
		String ak = qiniuInfo.getAk();
		String sk = qiniuInfo.getSk();
		Auth auth = Auth.create(ak, sk);
		
		// 绌洪棿鍚�
		String bucketname = qiniuInfo.getBucket();
		// 涓婁紶鐨勫浘鐗囧悕
		String key = UUID.randomUUID().toString().replace("-", "");
		
		file64 = file64.substring(22);
//		System.out.println("file64:"+file64);
		String url = "http://upload.qiniu.com/putb64/" + -1 + "/key/" + UrlSafeBase64.encodeToString(key);
		// 闈炲崕涓滅┖闂撮渶瑕佹牴鎹敞鎰忎簨椤� 1 淇敼涓婁紶鍩熷悕
		RequestBody rb = RequestBody.create(null, file64);
		String upToken  = auth.uploadToken(bucketname, null, 3600, new StringMap().put("insertOnly", 1));
		Request request = new Request.Builder()
				.url(url)
				.addHeader("Content-Type", "application/octet-stream")
				.addHeader("Authorization", "UpToken " + upToken)
				.post(rb).build();
//		System.out.println(request.headers());
		OkHttpClient client = new OkHttpClient();
		okhttp3.Response response = client.newCall(request).execute();
		System.out.println(response);
		
		String imgUrl = qiniuInfo.getDomain() + key;
		
		return imgUrl;
	}

}
