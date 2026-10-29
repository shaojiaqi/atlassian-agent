package io.zhile.crack.atlassian.agent;

import javassist.*;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author pengzhile
 * @link https://zhile.io
 * @version 1.0
 *
 * <p>2026 年容器化部署补丁（改动点）：
 * <ol>
 *   <li>注入代码改用 {@code java.util.Base64}，不再依赖 {@code javax.xml.bind.DatatypeConverter}。
 *       后者在 Java 11+ 已从 JDK 移除，Jira 9/10、Confluence 8/9/10 内嵌 JVM 上会 NoClassDefFoundError。</li>
 *   <li>{@code verifyLicenseHash} 的定位不再硬编码 Bitbucket 目录，改为：
 *       目标类自身的 ClassLoader → {@code -Datlassian.dir} / 环境变量 {@code ATLASSIAN_DIR} → 常见安装路径。
 *       各产品 lib 目录不同（Jira / Confluence / Bitbucket），硬编码会导致补丁静默失败。</li>
 *   <li>补丁失败时返回 null（放弃改写）而不是抛异常，避免影响应用正常启动。</li>
 * </ol>
 */
public class KeyTransformer implements ClassFileTransformer {
    private static final String CN_KEY_SPEC = "java/security/spec/EncodedKeySpec";

    private static final String LICENSE_DECODER_PATH = "com/atlassian/extras/decoder/v2/Version2LicenseDecoder";
    private static final String LICENSE_DECODER_CLASS = "com.atlassian.extras.decoder.v2.Version2LicenseDecoder";

    /** 产品自带 lib 目录的常见位置，按顺序尝试 */
    private static final String[] FALLBACK_LIB_DIRS = {
            "/opt/atlassian/jira/atlassian-jira/WEB-INF/lib",
            "/opt/atlassian/confluence/confluence/WEB-INF/lib",
            "/opt/atlassian/bitbucket/app/WEB-INF/lib",
            "/opt/atlassian/bamboo/atlassian-bamboo/WEB-INF/lib",
            "/opt/atlassian/crowd/crowd-webapp/WEB-INF/lib"
    };

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) {
            return classfileBuffer;
        }

        if (className.equals(CN_KEY_SPEC)) {
            return handleKeySpec(classfileBuffer);
        } else if (className.equals(LICENSE_DECODER_PATH)) {
            return handleLicenseDecoder(loader);
        }

        return classfileBuffer;
    }

    private byte[] handleKeySpec(byte[] classfileBuffer) {
        try {
            ClassPool cp = new ClassPool(true);
            cp.importPackage("java.util.Base64");

            int mod = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
            CtClass cc = cp.get(CN_KEY_SPEC.replace('/', '.'));
            CtClass cb = cp.get("byte[]");
            CtField cfOld = new CtField(cb, "__h_ok", cc);
            CtField cfNew = new CtField(cb, "__h_nk", cc);
            cfOld.setModifiers(mod);
            cfNew.setModifiers(mod);
            cc.addField(cfOld, "Base64.getDecoder().decode(\"MIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAIvfweZvmGo5otwawI3no7Udanxal3hX2haw962KL/nHQrnC4FG2PvUFf34OecSK1KtHDPQoSQ+DHrfdf6vKUJphw0Kn3gXm4LS8VK/LrY7on/wh2iUobS2XlhuIqEc5mLAUu9Hd+1qxsQkQ50d0lzKrnDqPsM0WA9htkdJJw2nS\");");
            cc.addField(cfNew, "Base64.getDecoder().decode(\"MIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAO0DidNibJHhtgxAnM9NszURYU25CVLAlwFdOWhiUkjrjOY459ObRZDVd35hQmN/cCLkDox7y2InJE6PDWfbx9BsgPmPvH75yKgPs3B8pClQVkgIpJp08R59hoZabYuvm7mxCyDGTl2lbrOi0a3j4vM5OoCWKQjIEZ28OpjTyCr3\");");
            CtConstructor cm = cc.getConstructor("([B)V");
            cm.insertBeforeBody("if(java.util.Arrays.equals($1,__h_ok)){$1=__h_nk;System.out.println(\"============================== agent working ==============================\");}");

            return cc.toBytecode();
        } catch (Throwable t) {
            System.out.println("atlassian-agent: EncodedKeySpec patch failed: " + t);
            return null;
        }
    }


    /**
     * 移除用于验证哈希的方法: <code>com.atlassian.extras.decoder.v2.Version2LicenseDecoder#verifyLicenseHash</code>
     *
     * @return 修改过的类的字节码；无法定位该类时返回 null（保持原样）
     */
    private byte[] handleLicenseDecoder(ClassLoader loader) {
        try {
            ClassPool cp = new ClassPool(true);

            // 1) 目标类所在的 ClassLoader 是找 com.atlassian.extras.* 最可靠的位置
            if (loader != null) {
                cp.insertClassPath(new LoaderClassPath(loader));
            }

            // 2) 显式指定 / 常见产品 lib 目录
            for (String dir : libDirs()) {
                File f = new File(dir);
                if (f.isDirectory()) {
                    cp.insertClassPath(dir);
                }
            }

            cp.importPackage("com.atlassian.extras.common.LicenseException");
            cp.importPackage("com.atlassian.extras.common.org.springframework.util.DefaultPropertiesPersister");
            cp.importPackage("com.atlassian.extras.decoder.api.AbstractLicenseDecoder");
            cp.importPackage("com.atlassian.extras.decoder.api.LicenseVerificationException");
            cp.importPackage("com.atlassian.extras.keymanager.KeyManager");
            cp.importPackage("com.atlassian.extras.keymanager.SortedProperties");
            cp.importPackage("java.io.ByteArrayInputStream");
            cp.importPackage("java.io.ByteArrayOutputStream");
            cp.importPackage("java.io.DataInputStream");
            cp.importPackage("java.io.DataOutputStream");
            cp.importPackage("java.io.IOException");
            cp.importPackage("java.io.InputStream");
            cp.importPackage("java.io.InputStreamReader");
            cp.importPackage("java.io.OutputStream");
            cp.importPackage("java.io.Reader");
            cp.importPackage("java.io.StringWriter");
            cp.importPackage("java.io.Writer");
            cp.importPackage("java.nio.charset.Charset");
            cp.importPackage("java.nio.charset.StandardCharsets");
            cp.importPackage("java.text.SimpleDateFormat");
            cp.importPackage("java.util.Date");
            cp.importPackage("java.util.Map");
            cp.importPackage("java.util.Properties");
            cp.importPackage("java.util.zip.Inflater");
            cp.importPackage("java.util.zip.InflaterInputStream");
            cp.importPackage("org.apache.commons.codec.binary.Base64");

            CtClass target = cp.getCtClass(LICENSE_DECODER_CLASS);
            CtMethod verifyLicenseHash = target.getDeclaredMethod("verifyLicenseHash");
            verifyLicenseHash.setBody("{System.out.println(\"atlassian-agent: skip license hash check\");}");

            return target.toBytecode();
        } catch (Throwable t) {
            System.out.println("atlassian-agent: license hash-check patch skipped (" + t + ")");
            return null;
        }
    }

    /**
     * 产品 lib 目录候选列表：-Datlassian.dir / ATLASSIAN_DIR 优先，其次常见安装路径。
     */
    private Set<String> libDirs() {
        Set<String> dirs = new LinkedHashSet<>();

        String property = System.getProperty("atlassian.dir");
        if (property != null && !property.trim().isEmpty()) {
            for (String dir : property.split(",")) {
                if (!dir.trim().isEmpty()) {
                    dirs.add(dir.trim());
                }
            }
        }

        String env = System.getenv("ATLASSIAN_DIR");
        if (env != null && !env.trim().isEmpty()) {
            for (String dir : env.split(",")) {
                if (!dir.trim().isEmpty()) {
                    dirs.add(dir.trim());
                }
            }
        }

        for (String dir : FALLBACK_LIB_DIRS) {
            dirs.add(dir);
        }

        return dirs;
    }

}
