package com.merico.selenium.page.factory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import com.merico.selenium.intercept.MethodInterceptController;
import com.merico.selenium.intercept.MethodInterceptControllerList;
import com.merico.selenium.page.Page;
import com.merico.selenium.utils.ReflectionUtils;

import net.sf.cglib.proxy.Enhancer;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.FindBys;
import org.openqa.selenium.support.pagefactory.FieldDecorator;

/**
 * PageFactory factory class
 * 
 */
public class PageFactory {
	private static MethodInterceptControllerList interceptorList = new MethodInterceptControllerList();
	
	/**
	 * Add method interceptor which is used to intercept the object being created
	 * @param interceptor
	 * @return
	 */
	public static boolean addInterceptor(MethodInterceptController interceptor) {
		return interceptorList.add(interceptor);
	}
	
	/**
	 * Remove interceptor for method
	 * @author 
	 * @param interceptor
	 */
	public static boolean removeInterceptor(MethodInterceptController interceptor) {
		return interceptorList.remove(interceptor);
	}

	/**
	 * Create a page object which will be injected through the dynamic Enhancer to create objects
	 * @param <T>
	 * @param pageClass To create a page object types
	 * @param driver WebDriver object which will be added to the parameter array of the page constructor
	 */
	public static <T extends Page> T createPage(Class<T> pageClass, WebDriver driver) {
		T page = instantiatePage(pageClass, driver);
		proxyFields(pageClass, driver, page);
		return page;
	}
	
	/**
	 * Create a Page object which will be dynamic injected through the Enhancer
	 * @param <T>
	 * @param pageClass To create a page object types
	 * @param driver WebDriver object which will be added to the parameter array of the page constructor
	 * @param args The parameter array of the Page constructor
	 */
	public static <T extends Page> T createPage(Class<T> pageClass, WebDriver driver, Object... args) {
		T page = instantiatePage(pageClass, driver, args);
		proxyFields(pageClass, driver, page);
		return page;
	}
	
	/**
	 * Create a Page object which will be dynamic injected through the Enhancer 
	 * @param <T>
	 * @param pageClass To create a page object types
	 * @param driver WebDriver object which will not be added to the parameter array of the page constructor
	 * @param argumentTypes The parameter type array of the Page constructor
	 * @param arguments The parameter array of the Page constructor
	 */
	public static <T extends Page> T createPage(Class<T> pageClass, WebDriver driver, Class<?>[] argumentTypes, Object[] arguments) {
		T page = instantiatePage(pageClass, argumentTypes, arguments);
		proxyFields(pageClass, driver, page);
		return page;
	}
	
	/**
	 * Instantiate a Page object
	 * @param <T>
	 * @param pageClass
	 * @param driver
	 */
	private static <T extends Page> T instantiatePage(Class<T> pageClass, WebDriver driver) {
		Class<?>[] argumentTypes = new Class<?>[] { driver.getClass() };
		Object[] arguments = new Object[] { driver };
		
		return instantiatePage(pageClass, argumentTypes, arguments);
	}

	/**
	 * Instantiate a Page object
	 * @param <T>
	 * @param pageClass
	 * @param driver
	 * @param args
	 * @return
	 */
	private static <T extends Page> T instantiatePage(Class<T> pageClass, WebDriver driver, Object... args) {
		Class<?>[] argTypes = getArgumentTypes(args);
		Class<?>[] argumentTypes = new Class<?>[args.length + 1];
		Object[] arguments = new Object[args.length + 1];
		argumentTypes[0] = driver.getClass();
		arguments[0] = driver;
		
		System.arraycopy(argTypes, 0, argumentTypes, 1, args.length);
		System.arraycopy(args, 0, arguments, 1, args.length);
		
		return instantiatePage(pageClass, argumentTypes, arguments);
	}

	/**
	 * According to whether to set up a callback, using different ways of instantiating a Page object
	 * @param <T>
	 * @param pageClass
	 * @param argumentTypes
	 * @param arguments
	 */
	private static <T extends Page> T instantiatePage(Class<T> pageClass, Class<?>[] argumentTypes, Object[] arguments) {
		Class<?>[] constructorParamTypes = null;
		try {
			constructorParamTypes = ReflectionUtils.getMatchedConstructor(pageClass, argumentTypes).getParameterTypes();
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
		
		if (hasCallback()) {
			return instantiatePageViaEnhancer(pageClass, constructorParamTypes, arguments);
		}
		else {
			return instantiatePageViaReflection(pageClass, constructorParamTypes, arguments);
		}
	}
	
	/**
	 * Instantiate a Page object with Reflection
	 * @param <T>
	 * @param pageClass
	 * @param argumentTypes
	 * @param arguments
	 */
	private static <T extends Page> T instantiatePageViaReflection(Class<T> pageClass, Class<?>[] argumentTypes, Object[] arguments) {
		try {
			Constructor<T> constructor = pageClass.getConstructor(argumentTypes);
			return constructor.newInstance(arguments);
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Instantiate a Page object with Enhancer
	 * @param <T>
	 * @param pageClass
	 * @param argumentTypes
	 * @param arguments
	 */
	@SuppressWarnings("unchecked")
	private static <T extends Page> T instantiatePageViaEnhancer(Class<T> pageClass, Class<?>[] argumentTypes, Object[] arguments) {
		Enhancer enhancer = getPageEnhancer(pageClass);
		return (T) enhancer.create(argumentTypes, arguments);
	}
	
	/**
	 * Get argument type array
	 * @param args
	 */
	private static Class<?>[] getArgumentTypes(Object[] args) {
		Class<?>[] result = new Class<?>[args.length];
		
		for (int i = 0; i < args.length; i++) {
			if (null == args[i]) {
				throw new IllegalArgumentException(String.format("The %sth argument is null.", i));
			}
			
			result[i] = args[i].getClass();
		}
		
		return result;
	}
	
	/**
	 * Whether to set up a callback
	 */
	private static boolean hasCallback() {
		return 0 < interceptorList.size();
	}
	
	private static Enhancer getPageEnhancer(Class<?> superClass) {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(superClass);
		enhancer.setCallback(interceptorList);
		
		return enhancer;
	}
	
	private static void proxyFields(Class<?> pageClass, WebDriver driver, Page page) {
		Class<?> proxyIn = pageClass;
		while (proxyIn != Object.class) {
			proxyFields(new LazyLoadFieldDecorator(driver), page, proxyIn);
			proxyIn = proxyIn.getSuperclass();
		}
	}

	/**
	 * Create a Page object
	 * This method has been replaced by createPage, reserved for compatibility with legacy code
	 * @param <T>
	 * @param page
	 * @param driver
	 */
	@Deprecated
	public static <T extends Page> T initPage(Class<T> page,
			WebDriver driver) {
		return createPage(page, driver);
	}

	/**
	 * Create a Page object
	 * This method has been replaced by createPage, reserved for compatibility with legacy code
	 * @param <T>
	 * @param page
	 * @param driver
	 * @param url
	 * @return
	 */
	@Deprecated
	public static <T extends Page> T initPage(Class<T> page,
			WebDriver driver, String url) {
		return createPage(page, driver, url);
	}

	public static void initElement(Page pageClassToProxy, WebDriver driver) {
		Class<?> proxyIn = pageClassToProxy.getClass();
		while (proxyIn != Object.class) {
			proxyFields(new LazyLoadFieldDecorator(driver), pageClassToProxy,
					proxyIn);
			proxyIn = proxyIn.getSuperclass();
		}
	}

	private static void proxyFields(FieldDecorator decorator, Object page,
			Class<?> proxyIn) {
		Field[] fields = proxyIn.getDeclaredFields();
		for (Field field : fields) {
			if (!assertValidAnnotations(field))
				continue;
			Object value = decorator.decorate(page.getClass().getClassLoader(),
					field);
			if (value != null) {
				try {
					field.setAccessible(true);
					field.set(page, value);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private static boolean assertValidAnnotations(Field field) {
		FindBys findBys = field.getAnnotation(FindBys.class);
		FindBy findBy = field.getAnnotation(FindBy.class);
		if (findBys == null && findBy == null) {
			return false;
		}
		return true;
	}
}
