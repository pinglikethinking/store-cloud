package com.graby.store.admin.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.graby.store.entity.Item;
import com.graby.store.entity.ShipOrder;
import com.graby.store.entity.Trade;
import com.graby.store.entity.TradeOrder;
import com.graby.store.remote.ExpressRemote;
import com.graby.store.remote.InventoryRemote;
import com.graby.store.remote.ItemRemote;
import com.graby.store.remote.ShipOrderRemote;
import com.graby.store.remote.TradeRemote;
import com.graby.store.service.InvAccounts;
import com.taobao.api.ApiException;


@Controller
@RequestMapping(value = "/trade/")
public class AdminTradeController {
	
	@Autowired
	private TradeRemote tradeRemote;
	
	@Autowired
	private InventoryRemote inventoryRemote;
	
	@Autowired
	private ItemRemote itemRemote;
	
	@Autowired
	private ShipOrderRemote shipOrderRemote;
	
	@Autowired
	private ExpressRemote expressRemote;
	
	/**
	 * 查询所有待审核订单
	 * @return
	 * @throws ApiException
	 */
	@RequestMapping(value = "waits", method=RequestMethod.GET)
	public String waits(Model model) throws ApiException {
		List<Trade> trades = tradeRemote.findWaitAuditTrades();
		model.addAttribute("trades", trades);
		return "/admin/tradeWaits";
	}
	
	/**
	 * 查询所有未关闭订单
	 * @return
	 * @throws ApiException
	 */
	@RequestMapping(value = "unfinish", method=RequestMethod.GET)
	public String unfinish(
			@RequestParam(value = "page", defaultValue = "1") int page, 
			Model model) throws ApiException {
		Page<Trade> trades = tradeRemote.findUnfinishedTrades(page, 15);
		model.addAttribute("trades", trades);
		return "/admin/tradeUnfinishs";
	}
	
	@RequestMapping(value = "delete/{id}", method=RequestMethod.GET)
	public String deleteTrade(@PathVariable(value = "id")Long tradeId) {
		tradeRemote.deleteTrade(tradeId);
		return "redirect:/trade/unfinish";
	}
	
	/**
	 * 查询所有待处理出库单
	 * @return
	 * @throws ApiException
	 */
	@RequestMapping(value = "send/waits", method=RequestMethod.GET)
	public String sendWaits(Model model) throws ApiException {
		List<ShipOrder> sendOrders  = shipOrderRemote.findSendOrderWaits();
		model.addAttribute("orders", sendOrders);
		return "/admin/sendOrderWaits";
	}
	
	/**
	 * 审核订单页面
	 * @return
	 * @throws ApiException
	 */
	@RequestMapping(value = "audit/{id}", method=RequestMethod.GET)
	public String audit(@PathVariable("id") Long id, Model model) throws ApiException {
		Trade trade = tradeRemote.getTrade(id);
		List<TradeOrder> orders = trade.getOrders();
		for (TradeOrder order : orders) {
			// 放置库存信息， 目前只支持单库存，如未来支持多库存这里要做改造
			Long itemId = order.getItem().getId();
			if (itemId == null) {
				order.setStockNum(-1);
			} else {
				long stockNum = inventoryRemote.getValue(1L, itemId, InvAccounts.CODE_SALEABLE);
				order.setStockNum(stockNum);
				Item item = itemRemote.getItem(itemId);
				order.setItem(item);
			}
		}
		model.addAttribute("trade", trade);
		return "/admin/tradeAudit";
	}
	
	/**
	 * 审核通过，创建出库单。
	 */
	@RequestMapping(value = "mkship/{id}")
	public String ship(@PathVariable("id") Long id, Model model) {
		ShipOrder sendOrder = tradeRemote.createSendShipOrderByTradeId(id);
		model.addAttribute("sendOrder", sendOrder);
		// "redirect:/trade/send/do/" + sendOrder.getId();
		return "redirect:/trade/waits";
	}
	

	
	/**
	 * 查询所有待拣货出库单
	 * @return
	 * @throws ApiException
	 */
	@RequestMapping(value = "send/pickings", method=RequestMethod.GET)
	public String pickingList(Model model) throws ApiException {
		List<ShipOrder> sendOrders  = shipOrderRemote.findSendOrderPickings(1L);
		model.addAttribute("orders", sendOrders);
		return "/admin/sendOrderPickings";
	}	
	
	/**
	 * 重置拣货单为运单打印状态
	 * @param ids
	 * @return
	 * @throws NumberFormatException
	 * @throws ApiException
	 */
	@RequestMapping(value = "send/express")
	public String pickings(@RequestParam(value = "ids", defaultValue = "") Long[] ids) throws NumberFormatException, ApiException {
		shipOrderRemote.reExpressShipOrder(ids);
		return "redirect:/trade/send/pickings";
	}
	
	/**
	 * 批量提交出库单
	 * @param ids
	 * @return
	 * @throws NumberFormatException
	 * @throws ApiException
	 */
	@RequestMapping(value = "send/submits")
	public String submits(@RequestParam(value = "ids", defaultValue = "") Long[] ids) throws NumberFormatException, ApiException {
		shipOrderRemote.submits(ids);
		return "redirect:/trade/send/pickings";
	}	
	
	
	/**
	 * 出库单处理页面 (打印分拣单、审核分拣单、打印快递运单、出库确认)
	 * @param orderId
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "send/do/{id}", method=RequestMethod.GET)
	public String doSendOrderForm(@PathVariable("id") Long orderId, Model model) {
		ShipOrder sendOrder = shipOrderRemote.getShipOrder(orderId);
		model.addAttribute("order", sendOrder);
		model.addAttribute("expressCompanys", expressRemote.getExpressMap());
		return "/admin/sendOrderForm";
	}
	
	/**
	 * 出库单提交，等待用户签收。
	 * @param orderId
	 * @param model
	 * @return
	 * @throws ApiException 
	 */
	@RequestMapping(value = "send/submit", method=RequestMethod.POST)
	public String submitOrder(ShipOrder order, Model model) throws ApiException {
		shipOrderRemote.submitSendOrder(order);
		return "redirect:/trade/send/waits";
	}
	
	/**
	 * 等待用户签收订单列表
	 * @param order
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "sign/waits")
	public String signWaits(
			@RequestParam(value = "q", defaultValue = "") String q,
			Model model) {
		List<ShipOrder> sendOrders  = shipOrderRemote.findSendOrderSignWaits();
		model.addAttribute("orders", sendOrders);
		return "/admin/signWaits";
	}
	
	/**
	 * 用户签收页面
	 * @param orderId
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "send/sign/{id}", method=RequestMethod.GET)
	public String signSendOrder(@PathVariable("id") Long orderId, Model model) {
		ShipOrder sendOrder = shipOrderRemote.getShipOrder(orderId);
		model.addAttribute("order", sendOrder);
		return "/admin/signForm";
	}
	
	/**
	 * 点击用户签收
	 * @param order
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "sign/submit/{id}", method=RequestMethod.GET)
	public String submitSign(@PathVariable(value = "id")Long orderId, Model model) {
		ShipOrder sendOrder = shipOrderRemote.signSendOrder(orderId);
		model.addAttribute("order", sendOrder);
		return "admin/signSuccess";
	}
	
}
